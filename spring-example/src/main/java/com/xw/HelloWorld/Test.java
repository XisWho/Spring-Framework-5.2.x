package com.xw.HelloWorld;

import com.xw.HelloWorld.Person;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {

	public static void main(String[] args) {
		// Spring容器的实例化
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext();
		context.register(Person.class);
		// Spring容器的初始化
		context.refresh();
		System.out.println(context.getBean(Person.class));
	}

}
