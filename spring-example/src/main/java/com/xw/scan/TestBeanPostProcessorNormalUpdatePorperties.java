package com.xw.scan;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Slf4j
@Component
public class TestBeanPostProcessorNormalUpdatePorperties implements BeanPostProcessor {

	public TestBeanPostProcessorNormalUpdatePorperties(){
		System.out.println("==TestBeanPostProcessorNormalUpdatePorperties create");
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if(beanName.equals("beanPostProcessorService")) {
			Class<?> aClass = bean.getClass();
			Field[] fs = aClass.getDeclaredFields();
			for (Field f : fs) {
				if(f.getName().equals("str")){
					f.setAccessible(true);

					try {
						f.set(bean,"spring");
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
			return bean;
		}
		return null;
	}
}