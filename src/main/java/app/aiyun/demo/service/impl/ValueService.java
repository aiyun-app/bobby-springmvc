package app.aiyun.demo.service.impl;

import app.aiyun.demo.service.IDemoService;
import app.aiyun.demo.service.IValueService;
import app.aiyun.mvcframework.annotation.BobbyService;

@BobbyService
public class ValueService implements IValueService, IDemoService {

    @Override
    public String get(String name) {
        return "ValueService, my name is" + name;
    }

    @Override
    public String getValue(Integer value) {
        return "ValueService, the value is " + value;
    }
}
