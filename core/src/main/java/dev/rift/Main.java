package dev.rift;

import dev.rift.loader.OpenApiLoader;
import dev.rift.model.Endpoint;

import java.util.List;

// Temporary entry point. It will be replaced by the Picocli CLI.
public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: Main <spec-file>");
            System.exit(2);
        }

        List<Endpoint> endpoints = OpenApiLoader.load(args[0]);
        System.out.println(endpoints.size() + " endpoints");
        for (Endpoint endpoint : endpoints) {
            System.out.println(endpoint.method() + " " + endpoint.pathTemplate());
        }
    }
}