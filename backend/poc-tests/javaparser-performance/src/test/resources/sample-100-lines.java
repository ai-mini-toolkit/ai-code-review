package com.example.test;

import java.util.*;
import java.io.*;
import java.time.*;
import java.util.concurrent.*;

/**
 * Auto-generated test class for performance testing
 * Target lines: 100
 */
public class TestClass100 {

    private String field0;
    private String field1;

    /**
     * Method documentation 0
     */
    public Map<String, Object> method0(String param1, int param2) {
        System.out.println("Executing method0");
        if (param2 > 0) {
            return new HashMap<>();
        }
        for (int i = 0; i < param2; i++) {
            System.out.println(i);
        }
        return new HashMap<>();
    }

    /**
     * Method documentation 1
     */
    public boolean method1(String param1, int param2) {
        System.out.println("Executing method1");
        if (param2 > 0) {
            return true;
        }
        for (int i = 0; i < param2; i++) {
            System.out.println(i);
        }
        return true;
    }

    /**
     * Method documentation 2
     */
    public void method2(String param1, int param2) {
        System.out.println("Executing method2");
        if (param2 > 0) {
            return ;
        }
        for (int i = 0; i < param2; i++) {
            System.out.println(i);
        }
        return;
    }

    /**
     * Method documentation 3
     */
    public boolean method3(String param1, int param2) {
        System.out.println("Executing method3");
        if (param2 > 0) {
            return true;
        }
        for (int i = 0; i < param2; i++) {
            System.out.println(i);
        }
        return true;
    }

    /**
     * Method documentation 4
     */
    public Map<String, Object> method4(String param1, int param2) {
        System.out.println("Executing method4");
        if (param2 > 0) {
            return new HashMap<>();
        }
        for (int i = 0; i < param2; i++) {
            System.out.println(i);
        }
        return new HashMap<>();
    }

    /**
     * Inner class 0
     */
    public static class InnerClass0 {

        private String innerField0;
        private int counter = 0;

        public InnerClass0() {
            this.innerField0 = "initialized";
        }

    }

}
