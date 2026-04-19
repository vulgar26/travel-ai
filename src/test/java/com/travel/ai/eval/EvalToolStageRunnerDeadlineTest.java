package com.travel.ai.eval;

import com.travel.ai.config.AppAgentProperties;
import com.travel.ai.config.AppEvalProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EvalToolStageRunnerDeadlineTest {

    @Test
    void effectiveDeadlineUsesSmallerOfEvalAndAgent() {
        AppEvalProperties eval = new AppEvalProperties();
        eval.setToolTimeoutMs(200L);
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofSeconds(10));
        EvalToolStageRunner r = new EvalToolStageRunner(eval, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(200L);
    }

    @Test
    void effectiveDeadlineWhenAgentIsStricter() {
        AppEvalProperties eval = new AppEvalProperties();
        eval.setToolTimeoutMs(500L);
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofMillis(80));
        EvalToolStageRunner r = new EvalToolStageRunner(eval, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(80L);
    }

    @Test
    void effectiveDeadlineFloorsAtTwentyMs() {
        AppEvalProperties eval = new AppEvalProperties();
        eval.setToolTimeoutMs(5L);
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofMillis(5));
        EvalToolStageRunner r = new EvalToolStageRunner(eval, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(20L);
    }
}
