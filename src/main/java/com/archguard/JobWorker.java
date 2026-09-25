package com.archguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

    private static final Logger logger = LoggerFactory.getLogger(JobWorker.class);

    private final SidecarExecutor executor = new SidecarExecutor();
    private final RuleEngine engine = new RuleEngine();

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void processJob(String repositoryUrl) {
        logger.info("\n[WORKER] Picked up job from queue: " + repositoryUrl);
        
        try {
            // Hardcoded local test paths for now to verify the pipeline
            String pythonScript = "sidecars/python/parser.py";
            String testFile = "src/test/resources/views/test_view.py";

            logger.info("[WORKER] Executing Python sidecar...");
            String astResult = executor.parsePythonFile(pythonScript, testFile);
            
            logger.info("[WORKER] Evaluating architectural boundaries...");
            engine.evaluateViewBoundary(astResult);
            
            logger.info("[WORKER] Job completed.\n");
        } catch (Exception e) {
            logger.error("[WORKER] Job failed: " + e.getMessage());
        }
    }
}