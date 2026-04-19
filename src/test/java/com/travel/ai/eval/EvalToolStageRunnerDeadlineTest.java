package com.travel.ai.eval;

import com.travel.ai.config.AppAgentProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EvalToolStageRunnerDeadlineTest {

    @Test
    void effectiveDeadlineUsesSmallerOfEvalAndAgent() {
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofSeconds(10));
        EvalToolStageRunner r = new EvalToolStageRunner(200L, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(200L);
    }

    @Test
    void effectiveDeadlineWhenAgentIsStricter() {
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofMillis(80));
        EvalToolStageRunner r = new EvalToolStageRunner(500L, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(80L);
    }

    @Test
    void effectiveDeadlineFloorsAtTwentyMs() {
        AppAgentProperties agent = new AppAgentProperties();
        agent.setToolTimeout(Duration.ofMillis(5));
        EvalToolStageRunner r = new EvalToolStageRunner(5L, agent);
        assertThat(r.effectiveToolDeadlineMs()).isEqualTo(20L);
    }
}
