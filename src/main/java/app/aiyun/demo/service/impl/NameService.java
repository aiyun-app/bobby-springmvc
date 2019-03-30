package app.aiyun.demo.service.impl;

import app.aiyun.demo.service.IDemoService;
import app.aiyun.mvcframework.annotation.BobbyService;

@BobbyService
public class NameService implements IDemoService {
    @Override
    public String get(String name) {
        return "NameService, My name is " + name;
    }
}
