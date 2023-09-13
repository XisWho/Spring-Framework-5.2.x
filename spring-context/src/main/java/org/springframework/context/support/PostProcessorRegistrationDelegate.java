/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 存放已经处理完（不是API提供的，为什么不需要？因为API提供的不会重复执行）的BeanFactoryPostProcessor以及它的子类BeanDefinitionRegistryPostProcessor
		// 防止重复执行
		Set<String> processedBeans = new HashSet<>();

		// 在Spring中这里一定为true，除非是SpringMVC等其他框架实现的容器
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

			// 存储所有实现了BeanFactoryPostProcessor的bean
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 存储所有实现了BeanDefinitionRegistryPostProcessor的bean
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 通过API直接插入到List<BeanFactoryPostProcessor> beanFactoryPostProcessors的postProcessor
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 如果实现了子类BeanDefinitionRegistryPostProcessor
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 执行回调方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					// 实现父类BeanFactoryPostProcessor的加入到一个list中
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 存储当前需要执行的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 根据类型到BeanDefinitionMap中查询，返回名字
			// 由于之前已经往BeanDefinitionMap中放入了一些BeanDefinition（Spring内置的）
			// 其中只有一个ConfigurationClassPostProcessor是实现了BeanDefinitionRegistryPostProcessor的
			// 所以这里会获取到
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 判断是否实现了PriorityOrdered接口
				// ConfigurationClassPostProcessor是实现了PriorityOrdered接口的
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// beanFactory.getBean的流程大概是这样的：获取Bean，如果Bean不存在，那么会去找对应的BeanDefinition，如果存在BeanDefinition则直接实例化
					// 所以这里会去实例化ConfigurationClassPostProcessor，放入单例池中
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 表示已经处理完了
					processedBeans.add(ppName);
				}
			}

			// 排序（之前说过的，根据order进行排序）
			// 由于目前只有ConfigurationClassPostProcessor，所以不需要关心排序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 合并 这是为了将来执行父类方法
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历执行，这里的话就会执行ConfigurationClassPostProcessor的postProcessBeanDefinitionRegistry方法，这个方法
			// 很重要，它完成了扫描，此时BeanDefinitionMap中就会多出了我们自定义的一些Bean（@Component标识）对应的BeanDefinition
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 清空，准备用于下次执行
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 再次寻找实现了BeanDefinitionRegistryPostProcessor的BeanName，包括之前的ConfigurationClassPostProcessor以及我们自定义的那些
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 由于processedBeans保存了ConfigurationClassPostProcessor
				// 所以这个判断的话就可以防止ConfigurationClassPostProcessor重复执行
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 当前需要执行
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 已经处理完了
					processedBeans.add(ppName);
				}
			}

			// 和上面一样
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 这里为什么会有第三次，甚至第四、第五次
			// 1. 第二次找出来的BeanDefinitionRegistryPostProcessor会动态添加新的BeanDefinitionRegistryPostProcessor
			//    比如说：
			// 		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			//			log.debug("i-s scan subclass postProcessBeanDefinitionRegistry PriorityOrdered");
			//			BeanDefinitionBuilder x= BeanDefinitionBuilder.genericBeanDefinition(X.class);
			//			// 这个x可能也是实现了BeanDefinitionRegistryPostProcessor
			//			registry.registerBeanDefinition("x",x.getBeanDefinition());
			// 		}
			// 2. 上面的条件判断，有些BeanDefinitionRegistryPostProcessor没有执行，beanFactory.isTypeMatch(ppName, Ordered.class)
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						// 如果找到了，后面还需要继续循环，继续找，原因同上
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 执行子类BeanDefinitionRegistryPostProcessor中的父类BeanFactoryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 执行通过API提供的直接实现了父类BeanFactoryPostProcessor的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 去BeanDefinitionMap中找实现了父类BeanFactoryPostProcessor的（包括Spring内置的以及使用@Component注解自定义的）
		// 实现了子类BeanDefinitionRegistryPostProcessor的也会找出来，但是已经处理过了，所以会通过判断过滤
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 实现了priorityOrdered的父类集合
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 实现了order接口的父类集合（实现了priorityOrdered的也会放入）
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 没有任何实现
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			// 如果之前已经处理过了，则不再处理
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 这里保存的是实例化后的对象
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				// 而这里保存的只是名称
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 这里保存的也只是名称
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 执行priorityOrderedPostProcessors中的
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 执行orderedPostProcessorNames中的
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			// 在这里进行了实例化
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 执行nonOrderedPostProcessorNames中的
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 在这里进行了实例化
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	/**
	 * 查找并注册BeanPostProcessors
	 * @param beanFactory
	 * @param applicationContext
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// 从BeanDefinitionMap中获取所有的BeanPostProcessor
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		//
		// beanProcessorTargetCount是Spring期望一个Bean应该执行几个BeanPostProcessor的数量
		// beanFactory.getBeanPostProcessorCount()是List<BeanPostProcessor> beanPostProcessors的元素个数
		// 3+1+2 = 6
		// 如果启用AOP，那么BeanDefinitionMap还多一个，所以是3+1+3=7
		// 如果自定义了BeanPostProcessor，那么还要加上去
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 这里还添加了一个BeanPostProcessor，这个的作用是：检查一个Bean即将执行的BeanPostProcessor的个数和预期的是否相同
		// 为什么要检查？
		// 预期值？实际的值找出来的BeanPostProcessor仅仅是Spring认为将来有一天会放到这个list当中的给bean做后置处理
		// Spring将来也一定会放进去，现在还没有放进去
		// 假设在没有放进去之前就有了某些Bean前去实例化，
		// 哪些情况会出现预期和实际不符合：
		/**
		 * 在add BeanPostProcessorChecker之后，并且在BeanPostProcessors没有添加到预期的所有对象之前，有了一个Bean，开始走生命周期
		 */
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 存放所有实现了priorityOrdered接口的BeanPostProcessors对象
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 先不管这个
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		// 存放所有实现了ordered接口的BeanPostProcessors的Bean的名字
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 普通的BeanPostProcessors的Bean的名字
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();

		// BeanDefinitionMap中的
		for (String ppName : postProcessorNames) {
			// 是否实现了priorityOrdered接口
			// AutowiredAnnotationBeanPostProcessor和CommonAnnotationBeanPostProcessor满足
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// getBean完成实例化
				// 但是实例化的过程并不会去调用BeanPostProcessorChecker进行验证
				// 可以看一下BeanPostProcessorChecker的逻辑，beanPostProcessor的实例化并不会参与BeanPostProcessorChecker的校验
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			// 是否实现了Ordered接口，AnnotationAwareAspectJAutoProxyCreator满足
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// priorityOrderedPostProcessors排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 注册priorityOrderedPostProcessors到List<BeanPostProcessor> beanPostProcessors
		// AutowiredAnnotationBeanPostProcessor和CommonAnnotationBeanPostProcessor会放进去
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			// 通过名字调用getBean方法
			// 会实例化AnnotationAwareAspectJAutoProxyCreator
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 注册AnnotationAwareAspectJAutoProxyCreator到List<BeanPostProcessor> beanPostProcessors
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			// 父类BeanFactoryPostProcessor的方法
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
