package app.aiyun.demo.controller;

import app.aiyun.demo.service.IDemoService;
import app.aiyun.demo.service.impl.ValueService;
import app.aiyun.mvcframework.annotation.BobbyAutowired;
import app.aiyun.mvcframework.annotation.BobbyController;
import app.aiyun.mvcframework.annotation.BobbyRequestMapping;
import app.aiyun.mvcframework.annotation.BobbyRequestParam;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@BobbyController
@BobbyRequestMapping("/demo")
public class DemoAction {

	@BobbyAutowired
	private IDemoService demoService;

  	@BobbyAutowired("nameService")
	private IDemoService demoService1;

	@BobbyAutowired
  	private IDemoService demoService2;

	@BobbyAutowired
	private ValueService valueService;


	@BobbyRequestMapping("/query.*")
	public void query(HttpServletRequest req, HttpServletResponse resp,
                      @BobbyRequestParam("name")  String name,
					  @BobbyRequestParam("age") Integer age){
		String result = "demoService " + demoService.get(name) + "\r\n";
		result += "demoService1 " + demoService1.get(name) + "\r\n";
		result += "demoService2 " + demoService2.get(name) + "\r\n";
		result += "valueService " + valueService.get(name) + " " +valueService.getValue(age);


		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@BobbyRequestMapping("/add")
	public void add(HttpServletRequest req, HttpServletResponse resp,
                    @BobbyRequestParam("a") Integer a, @BobbyRequestParam("b") Integer b){
		try {
			resp.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@BobbyRequestMapping("/sub")
	public void add(HttpServletRequest req, HttpServletResponse resp,
                    @BobbyRequestParam("a") Double a, @BobbyRequestParam("b") Double b){
		try {
			resp.getWriter().write(a + "-" + b + "=" + (a - b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@BobbyRequestMapping("/remove")
	public String  remove(@BobbyRequestParam("id") Integer id){
		return "" + id;
	}

}
