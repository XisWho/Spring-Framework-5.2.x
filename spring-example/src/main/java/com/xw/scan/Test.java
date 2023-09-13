package com.xw.scan;

import sun.misc.ProxyGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;

public class Test {

	public static void main(String[] args) throws IOException {
		URL resource = Test.class.getClass().getResource("/");

		byte[] bytes = ProxyGenerator.generateProxyClass("$Proxy0", new Class[]{UserDao.class});

		File file = new File(resource.getPath(), "$Proxy0.class");
		if (!file.exists()) {
			file.createNewFile();
		}
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(bytes);
		fos.flush();
		fos.close();

		Proxy
	}

}
