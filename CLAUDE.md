# MSC Church Website — Backend

## Stack
- Java 21, Spring Boot 3.x, Maven
- MySQL 8 (utf8mb4, utf8mb4_unicode_ci) — REQUIRED for Korean text
- Spring Web, Data JPA, Security, Validation
- Lombok, MapStruct, Flyway, JJWT (io.jsonwebtoken)
- springdoc-openapi for Swagger UI

## Package structure
com.msc.church
├── config         SecurityConfig, JwtConfig, CorsConfig, WebConfig
├── auth           login, refresh, JWT filter, JwtService, AuthController, RefreshToken entity
├── member         entity, repository, service, controller, dto, mapper
├── cell           Cell entity + CellMembership entity + CellTransferSuggestion entity
├── bulletin       Bulletin + 4 child entities
├── sermon
├── attendance     ServiceAttendance entity (cell attendance lives in bulletin module)
└── common         BaseEntity, ApiResponse, GlobalExceptionHandler, ErrorCode, BusinessException

## Conventions

### Entities
- All entities extend `BaseEntity` (id, createdAt, updatedAt via @MappedSuperclass + JPA Auditing).
- Soft delete via `@SQLDelete` + `@Where(clause = "deleted_at IS NULL")` for entities that need it (members).
- Enum-like VARCHAR columns: use `@Enumerated(EnumType.STRING)`. Never ORDINAL.
- `LocalDate` for DATE, `LocalDateTime` for DATETIME.
- LONGTEXT: `@Column(columnDefinition = "LONGTEXT")` + String, or `@Lob`.
- JSON columns: store as String, parse in service layer with Jackson (V1 simplicity).

### Controllers
- Always return `ApiResponse<T>` — never raw entities, never `ResponseEntity<Map>`.
- Endpoints under `/api/v1/...`.
- Use `@PreAuthorize` for role checks on controller methods.
- Validation via `@Valid` on request DTOs.
- Pagination: accept `Pageable`, return `Page<T>` wrapped in ApiResponse.

### DTOs
- Separate request/response: `MemberCreateRequest`, `MemberUpdateRequest`, `MemberResponse`, `MemberSummary`, `MemberDetail`.
- Records preferred for immutable response DTOs.
- Map between entities and DTOs with MapStruct.
- Never expose entities directly through controllers.

### Services
- Constructor injection only. Use Lombok `@RequiredArgsConstructor`.
- All write methods `@Transactional`.
- Read-only methods `@Transactional(readOnly = true)` if performance matters.
- Throw custom exceptions extending `BusinessException`. Never raw RuntimeException for business errors.

### Repositories
- Spring Data JPA interfaces.
- Return `Optional<T>` for single results, `Page<T>` for paginated, `List<T>` for unpaginated lists.
- Custom queries via `@Query` JPQL. Avoid native queries unless necessary (then document why).

### Exceptions
- Custom: extend `BusinessException` with an `ErrorCode` enum value.
- Examples: `MemberNotFoundException`, `DuplicateEmailException`, `BulletinAlreadyPublishedException`.
- Handled centrally in `GlobalExceptionHandler` → translates to `ApiResponse` error envelope.

## Security
- JWT access token (15 min) + refresh token (7 days, hashed in DB).
- BCrypt for passwords (work factor 12).
- Roles: ADMIN, PASTOR, LEADER, MEMBER.
- Stateless: no HTTP session.
- All `/api/v1/auth/**` open; everything else requires authentication.
- Refresh token in httpOnly cookie, SameSite=Strict, Secure in prod.

## Database

### Flyway rules
- Migrations in `src/main/resources/db/migration/` named `V{n}__{description}.sql`.
- NEVER edit a shipped migration. Add new V{n+1}.
- All tables use utf8mb4 charset and utf8mb4_unicode_ci collation.
- All FKs explicit with names: `fk_{table}_{column}`.
- All indexes named: `idx_{table}_{columns}` or `uk_{table}_{columns}` for unique.

### JPA tips
- Use `FetchType.LAZY` everywhere unless you have a reason. EAGER causes N+1.
- For collections, use `@OneToMany(mappedBy = ..., cascade = ALL, orphanRemoval = true)` when child entities have no independent existence.
- Use `@BatchSize` for known LAZY-loaded collections to mitigate N+1.

## Internationalization
- NEVER hardcode Korean or English in error messages.
- Use `MessageSource` with `messages_ko.properties` + `messages_en.properties`.
- Resolve locale from `Accept-Language` header → user.preferredLocale → KR default.

## Testing

### Unit tests (services)
- JUnit 5 + Mockito.
- Pattern: `MemberServiceTest` is the canonical example.
- Test file co-located: `src/test/java/com/msc/church/member/MemberServiceTest.java`.
- Cover: happy path, NotFoundException, validation errors, edge cases.

### Integration tests
- `@SpringBootTest` + Testcontainers MySQL.
- Test full request → response cycle for each controller.
- Use `@Sql` for setup data when needed.

### Don't write
- Tests for getters/setters
- Tests for trivial mappers
- Tests of Spring Framework itself

## Logging
- SLF4J via Lombok `@Slf4j`.
- INFO for business events (member created, bulletin published).
- WARN for recoverable issues (validation failures, expected NotFound).
- ERROR for unexpected exceptions.
- Never log passwords, tokens, or full PII.
- Structured logging where useful: `log.info("Member created: id={}, email={}", id, email)`.

## Commits
- Conventional commits: `feat(member): add CRUD endpoints`, `fix(auth): refresh token expiry bug`, `chore(deps): bump spring boot to 3.2.5`, `refactor(bulletin): simplify child collection update`.
- Commit after every working feature. Never commit broken code.

## When in doubt
- Check the planning docs in `../files/` (the 12 planning markdown files).
- Match the pattern from `member` module — it's the canonical reference.
- Prefer simple over clever. This is V1.
- If a generated solution feels overengineered, push back and simplify.
