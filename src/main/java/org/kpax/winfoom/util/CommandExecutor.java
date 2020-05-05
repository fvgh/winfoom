/*
 * Copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

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

    /**
     * Retrieves the system proxy line by querying the Windows Registry.
     * @return the system proxy line
     * @throws CommandExecutionException
     */
    public static Optional<String> getSystemProxy() throws CommandExecutionException {
        String[] command = new String[]{"cmd.exe", "/c",
                "reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" | findstr \"ProxyServer AutoConfigURL\""};

        logger.info("Execute command: {}", String.join(" ", command));
        List<String> output = CommandExecutor.execute(command);
        logger.info("Output: {}", String.join("\n", output));
        return output.stream().filter((item) -> item.trim().startsWith("ProxyServer"))
                .map((item) -> {
                    String[] split = item.trim().split("\\s");
                    return split[split.length - 1];
                })
                .findFirst();
    }

}
