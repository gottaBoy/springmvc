package com.mysting.springmvc.demo.controller;

import com.mysting.springmvc.annotation.MyAutowired;
import com.mysting.springmvc.annotation.MyController;
import com.mysting.springmvc.annotation.MyRequestMapping;
import com.mysting.springmvc.annotation.MyRequestParam;
import com.mysting.springmvc.demo.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/demo")
public class DemoController {

    @MyAutowired
    private DemoService demoService;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest req,
                      HttpServletResponse res,
                      @MyRequestParam("name") String name) {
        System.out.println("name: " + name);
        String result = demoService.get(name);

        try{
            res.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("/add")
    public void add(HttpServletRequest req,
                    HttpServletResponse res,
                    @MyRequestParam("a") Integer a,
                    @MyRequestParam("b") Integer b) {
        try {
            res.getWriter().write(String.format("%d+%d=%d", a, b, (a+b)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
