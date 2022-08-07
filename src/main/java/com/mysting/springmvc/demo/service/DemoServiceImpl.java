package com.mysting.springmvc.demo.service;

import com.mysting.springmvc.annotation.MyService;

@MyService
public class DemoServiceImpl implements DemoService {
    public String get(String name) {
        return String.format("My name is %s.", name);
    }
}
