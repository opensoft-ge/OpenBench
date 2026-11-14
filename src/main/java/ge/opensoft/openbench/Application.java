package ge.opensoft.openbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;

@SpringBootApplication
public class Application {
    static java.util.function.IntConsumer exit = System::exit;

    public static void main(String[] args) {
        exit.accept(launch(args, System.out));
    }

    public static int launch(String[] args, PrintStream output) {
        try (var context = new SpringApplication(Application.class).run()) {
            return context.getBean(Cli.class).run(args, output);
        }
    }
}
