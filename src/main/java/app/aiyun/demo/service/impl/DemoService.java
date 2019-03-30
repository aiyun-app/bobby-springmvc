package app.aiyun.demo.service.impl;

import app.aiyun.demo.service.IDemoService;
import app.aiyun.mvcframework.annotation.BobbyService;


/**
 * 核心业务逻辑
 */
@BobbyService
public class DemoService implements IDemoService {

	public String get(String name) {

		return "DemoService, My name is " + name;
	}

}
