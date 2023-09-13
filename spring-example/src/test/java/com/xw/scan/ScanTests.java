package com.xw.scan;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ScanTests {
	@Test
	public void testBeanPostProcessor(){
		System.out.println("Hello");
		AnnotationConfigApplicationContext context
				= new AnnotationConfigApplicationContext(App.class);
		context.register(App.class);
		context.refresh();
		BeanPostProcessorService bean = context.getBean(BeanPostProcessorService.class);
		String str = bean.getStr();
		Integer i = bean.getI();
		//log.debug("str[{}],I[{}]",str,i);
	}

}
