package com.kosaki.storage;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    static StorageSystem syscat;

    public static void main(String[] args) throws IOException {

        Scanner sc = new Scanner(System.in);
        boolean systemloop = true;
        int result = -1;

        initST("syscat");

        System.out.println("Welcome to the Management System Demo! Initiate one of the 7 functions to get started!");

        while (systemloop){
            System.out.println("Please choose a number from 1 to 7, or press 0 to exit.");
            System.out.println("1) Create Type");
            System.out.println("2) Delete Type");
            System.out.println("3) List All Types");
            System.out.println("4) Create Record");
            System.out.println("5) Delete Record");
            System.out.println("6) Find Record by Key");
            System.out.println("7) List All Records of Type");
            System.out.println("0) Exit");


            boolean validinput = false;

            while (!validinput) {
                String input = sc.next();
                try {
                    result = Integer.parseInt(input);
                    validinput = true;
                } catch (NumberFormatException e) {
                    System.out.println("That's not a valid number input.");
                }
            }

            switch (result){
                case 1:
                    syscat.createType();
                    break;
                case 2:
                    syscat.deleteType();
                    break;
                case 3:
                    syscat.listAllTypes();
                    break;
                case 4:
                    syscat.createRecord();
                    break;
                case 5:
                    syscat.deleteRecord();
                    break;
                case 6:
                    syscat.findRecordByKey();
                    break;
                case 7:
                    syscat.listRecords();
                    break;
                case 0:
                    systemloop = false;
                    break;
                default:
                    break;
            }

        }

        System.out.println("Bye!");
    }

    public static void initST(String filename) throws IOException {
        syscat = new StorageSystem(filename);
    }

}
