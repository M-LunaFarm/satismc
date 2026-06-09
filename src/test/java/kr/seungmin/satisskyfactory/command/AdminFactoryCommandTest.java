package kr.seungmin.satisskyfactory.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFactoryCommandTest {
    @Test
    void exposesDedicatedAdminCommandEntrypoints() throws Exception {
        assertTrue(Modifier.isFinal(AdminFactoryCommand.class.getModifiers()));
        assertEquals(boolean.class, AdminFactoryCommand.class
                .getMethod("execute", CommandSender.class, String[].class)
                .getReturnType());
        assertEquals(List.class, AdminFactoryCommand.class
                .getMethod("complete", CommandSender.class, String[].class)
                .getReturnType());
    }
}
