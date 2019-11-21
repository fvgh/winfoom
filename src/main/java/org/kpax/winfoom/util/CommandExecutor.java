package org.kpax.winfoom.util;

import org.apache.commons.io.IOUtils;
import org.kpax.winfoom.exception.CommandExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for executing command line instructions.
 */
public class CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);

    /**
     * Executes a command with parameters.
     *
     * @param command The command's parameters.
     * @return The command's output.
     * @throws CommandExecutionException
     */
    public static List<String> execute(String... command) throws CommandExecutionException {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(command);
            Process process = builder.start();
            return IOUtils.readLines(new InputStreamReader(process.getInputStream()));
        } catch (IOException e) {
            throw new CommandExecutionException(e);
        }
    }


    public static Optional<String> getSystemProxy() throws CommandExecutionException {
        String[] command = new String[]{"cmd.exe", "/c",
                "reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" | findstr \"ProxyServer AutoConfigURL\""};

        logger.info("Execute command: {}", String.join(" ", command));
        List<String> output = CommandExecutor.execute(command);
        logger.info("Output: {}", String.join("\n", output));
        String tag = "ProxyServer";
        return output.stream().filter((item) -> item.trim().startsWith(tag))
                .map((item) -> {
                    String[] split = item.trim().split("\\s");
                    return split[split.length - 1];
                })
                .findFirst();
    }

}
