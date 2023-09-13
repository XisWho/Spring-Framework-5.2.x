package com.xw.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TestBeanPostProcessorNormal implements BeanPostProcessor {

	@Autowired
	BeanPostProcessorService beanPostProcessorService;

	public TestBeanPostProcessorNormal(){
		System.out.println("==TestBeanPostProcessorNormal create");
	}

	public void printfInfo(){
		System.out.println("TestBeanPostProcessorNormal beanPostProcessorService[{}]  "+beanPostProcessorService);
	}

}