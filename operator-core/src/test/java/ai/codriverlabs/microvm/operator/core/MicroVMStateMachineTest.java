package ai.codriverlabs.microvm.operator.core;

import ai.codriverlabs.microvm.operator.core.enums.MicroVMState;
import ai.codriverlabs.microvm.operator.core.state.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static ai.codriverlabs.microvm.operator.core.enums.MicroVMState.*;
import static org.junit.jupiter.api.Assertions.*;

class MicroVMStateMachineTest {

    private final MicroVMStateMachine sm = new MicroVMStateMachine();

    @Test
    void pendingCanTransitionToRunningOrFailed() {
        var result = sm.transition(PENDING, RUNNING);
        assertInstanceOf(StateTransitionResult.Valid.class, result);

        var result2 = sm.transition(PENDING, FAILED);
        assertInstanceOf(StateTransitionResult.Valid.class, result2);

        var invalid = sm.transition(PENDING, TERMINATED);
        assertInstanceOf(StateTransitionResult.Invalid.class, invalid);
    }

    @Test
    void runningCanTransitionToSuspendingOrTerminating() {
        assertInstanceOf(StateTransitionResult.Valid.class, sm.transition(RUNNING, SUSPENDING));
        assertInstanceOf(StateTransitionResult.Valid.class, sm.transition(RUNNING, TERMINATING));
    }

    @Test
    void terminatedHasNoValidTransitions() {
        Set<MicroVMState> targets = sm.validTargets(TERMINATED);
        assertTrue(targets.isEmpty(), "Terminated should have no valid transitions");
    }

    @Test
    void failedCanRetryOrTerminate() {
        assertInstanceOf(StateTransitionResult.Valid.class, sm.transition(FAILED, PENDING));
        assertInstanceOf(StateTransitionResult.Valid.class, sm.transition(FAILED, TERMINATING));
    }

    @Test
    void terminatingCanOnlyGoToTerminated() {
        Set<MicroVMState> targets = sm.validTargets(TERMINATING);
        assertEquals(Set.of(TERMINATED), targets);
    }

    @Test
    void invalidTransitionContainsDescriptiveReason() {
        var result = sm.transition(PENDING, TERMINATED);
        assertInstanceOf(StateTransitionResult.Invalid.class, result);
        var invalid = (StateTransitionResult.Invalid) result;
        assertTrue(invalid.reason().contains("Pending"), "Reason should mention source state");
        assertTrue(invalid.reason().contains("Terminated"), "Reason should mention target state");
    }

    @ParameterizedTest
    @EnumSource(MicroVMState.class)
    void selfTransitionIsAlwaysInvalid(MicroVMState state) {
        // No state should transition to itself
        var result = sm.transition(state, state);
        assertInstanceOf(StateTransitionResult.Invalid.class, result);
    }

    @ParameterizedTest
    @EnumSource(MicroVMState.class)
    void everyStateHasDefinedValidTargets(MicroVMState state) {
        // Should not throw
        Set<MicroVMState> targets = sm.validTargets(state);
        assertNotNull(targets);
    }
}
