package com.xw.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BeanPostProcessorService {

	String str;
	Integer i;
	public BeanPostProcessorService(){
		System.out.println("==BeanPostProcessorService create");
	}

	public Integer getI() {
		return i;
	}

	public String getStr() {
		return str;
	}

	public void testAop(){
		System.out.println("--------BeanPostProcessorService logic-----");

	}


}
