package kr.seungmin.satisskyfactory.task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineTickServiceTest {
    @Test
    void keepsAnExplicitActiveMachineQueue() throws Exception {
        Field queue = MachineTickService.class.getDeclaredField("activeMachineQueue");
        Field queuedMachines = MachineTickService.class.getDeclaredField("queuedMachines");

        assertEquals(Queue.class, queue.getType());
        assertEquals(Set.class, queuedMachines.getType());
    }
}
