package edu.stusystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
public class StudentSystemApplication {
    public static void main(String[] args) {
        boolean init = args.length > 0 && "init-admin".equals(args[0]);
        boolean recoverAdmin = args.length > 0 && "recover-admin".equals(args[0]);
        String[] springArgs = init || recoverAdmin ? Arrays.copyOfRange(args, 1, args.length) : args;
        var application = new SpringApplication(StudentSystemApplication.class);
        if (recoverAdmin) {
            springArgs = Arrays.copyOf(springArgs, springArgs.length + 2);
            springArgs[springArgs.length - 2] = "--server.address=127.0.0.1";
            springArgs[springArgs.length - 1] = "--server.port=0";
        }
        var context = application.run(springArgs);
        if (init) {
            var identity = context.getBean(edu.stusystem.service.IdentityService.class);
            identity.initializeAdmin(requiredEnv("ADMIN_NO"), requiredEnv("ADMIN_USERNAME"), requiredEnv("ADMIN_NAME"), requiredEnv("ADMIN_PASSWORD"));
            System.out.println("Initial administrator created.");
            context.close();
        }
        if (recoverAdmin) {
            try {
                if (!isLocalProfile(context.getEnvironment())) {
                    throw new IllegalStateException("Administrator recovery is restricted to the local profile");
                }
                var identity = context.getBean(edu.stusystem.service.IdentityService.class);
                long accountId = identity.singleActiveAdministratorId();
                String username = identity.recoverAdminPassword(accountId, requiredEnv("ADMIN_RECOVERY_PASSWORD"));
                System.out.println("Administrator password reset for username: " + username);
            } finally {
                context.close();
            }
        }
    }

    private static boolean isLocalProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        return Arrays.asList(active.length == 0 ? environment.getDefaultProfiles() : active).contains("local");
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required environment variable " + name);
        return value;
    }
}
