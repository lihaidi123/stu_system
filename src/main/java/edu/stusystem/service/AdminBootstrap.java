package edu.stusystem.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final IdentityService identity;
    public AdminBootstrap(IdentityService identity) { this.identity = identity; }
    @Override public void run(ApplicationArguments args) {
        if (!args.containsOption("init-admin")) return;
        String businessNo = required(args, "admin-no"), username = required(args, "admin-username"), realName = required(args, "admin-name"), password = required(args, "admin-password");
        identity.initializeAdmin(businessNo, username, realName, password);
        System.out.println("Initial administrator created.");
        System.exit(0);
    }
    private static String required(ApplicationArguments args, String name) {
        return args.getOptionValues(name) == null || args.getOptionValues(name).isEmpty() ? fail(name) : args.getOptionValues(name).getFirst();
    }
    private static String fail(String name) { throw new IllegalArgumentException("Missing required option --" + name); }
}
