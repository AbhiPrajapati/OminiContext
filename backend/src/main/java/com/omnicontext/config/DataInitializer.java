package com.omnicontext.config;

import com.omnicontext.dto.CreateContextRequest;
import com.omnicontext.repository.ContextCapsuleRepository;
import com.omnicontext.service.ContextService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ContextService contextService;
    private final ContextCapsuleRepository repository;

    public DataInitializer(ContextService contextService, ContextCapsuleRepository repository) {
        this.contextService = contextService;
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            contextService.recompressAll();
            return;
        }

        // 1. Fullstack Auth & Microservices Context
        CreateContextRequest ctx1 = new CreateContextRequest();
        ctx1.setTitle("Enterprise Auth & Refresh Token Flow");
        ctx1.setProject("FintechCloud");
        ctx1.setDescription("OAuth2 + JWT access/refresh rotation architecture with Spring Boot & Angular");
        ctx1.setTags("Spring Boot, Angular, JWT, OAuth2, Redis");
        ctx1.setCompressionStrategy("SEMANTIC_DENSE");
        ctx1.setRawContent("""
User: Hi Claude! Can you help us design the authentication pipeline for our banking application?
Assistant: Hello! I'd be happy to help with that. What are your specific requirements?
User: We are using Spring Boot on the backend and Angular on the frontend.
We decided to use JWT access tokens with a 15-minute expiration stored in memory, and HttpOnly secure refresh cookies stored in Redis for revocation.
Assistant: That sounds like a solid security architecture! Here are the details...
User: Currently, our task is fixing the refresh route when the token version in the database is incremented.
Status: error 401 Unauthorized occurs intermittently when two requests trigger refresh at the same time.
Rule: Always ensure token rotation is atomic and use Redis distributed lock.
Rule: Never expose the refresh token to JavaScript window scope.
Next step: Implement a mutex lock in the Angular HTTP interceptor to queue pending requests while refresh is ongoing.
""");
        var saved1 = contextService.createContext(ctx1);
        contextService.addNote(saved1.getId(), new com.omnicontext.dto.AddNoteRequest() {{
            setAuthorName("Alex (Lead SecOps)");
            setNote("Confirmed that Redis sliding window expiration matches our 7-day cookie TTL policy.");
        }});

        // 2. E-Commerce Order Fulfillment State Machine
        CreateContextRequest ctx2 = new CreateContextRequest();
        ctx2.setTitle("Order Processing State Machine & Saga Pattern");
        ctx2.setProject("ShopPulse");
        ctx2.setDescription("Orchestration-based Saga workflow for checkout, payments, and inventory");
        ctx2.setTags("Java, Kafka, PostgreSQL, Saga, Microservices");
        ctx2.setCompressionStrategy("SEMANTIC_DENSE");
        ctx2.setRawContent("""
Hey ChatGPT, let's document our order saga state machine.
Tech stack: Java, Kafka, PostgreSQL, Docker, Kubernetes.
Decision: We decided to use orchestration-based Saga instead of choreography to keep transaction compensation predictable.
State transitions:
- ORDER_CREATED -> PAYMENT_PENDING
- PAYMENT_SUCCESS -> INVENTORY_RESERVED -> ORDER_CONFIRMED
- PAYMENT_FAILED -> ORDER_CANCELLED (Compensation triggered)
Rule: Every Kafka event must include idempotency-key in the header.
Constraint: Maximum payment retry count is 3 before triggering compensation.
Task: Write integration tests for payment failure rollback logic using Testcontainers.
""");
        contextService.createContext(ctx2);

        // 3. AI Pipeline & Model Context Protocol Gateway
        CreateContextRequest ctx3 = new CreateContextRequest();
        ctx3.setTitle("Multi-Agent RAG Pipeline & MCP Gateway");
        ctx3.setProject("OmniContext");
        ctx3.setDescription("Context compression and distillation protocols across LLM providers");
        ctx3.setTags("TypeScript, Python, MCP, FastMCP, VectorDB");
        ctx3.setCompressionStrategy("STATE_KV");
        ctx3.setRawContent("""
Team meeting notes on AI context distillation:
We decided to use an AST-based shorthand representation to compress prompts by 70%.
Decision: Every context capsule must expose /raw, /prompt, and /mcp endpoints.
Constraint: Response time for /raw endpoint must remain under 20ms to allow zero-lag AI fetching.
Task: Integrate Angular dashboard with real-time token savings indicator and 1-click clipboard prompt generator.
""");
        contextService.createContext(ctx3);
    }
}
