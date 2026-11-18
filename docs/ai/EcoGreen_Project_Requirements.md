# ECOGREEN COMMERCE & MANAGEMENT PLATFORM

## PROJECT REQUIREMENTS / SOFTWARE REQUIREMENTS SPECIFICATION (SRS)

**Version:** 1.0

**Project direction:** Production-oriented single-business e-commerce and management system

**Document purpose:** Define the target system clearly enough to guide architecture, database, API, frontend, backend, testing, deployment, and future production development.

# 1. Project Overview

## 1.1 Project name

EcoGreen Commerce & Management Platform

## 1.2 Project purpose

The project is a production-oriented e-commerce and business management platform for a single business. Customers use the storefront to discover products, manage a cart, checkout, pay, and track their own orders. Authorized managers or administrators operate the business side of the system, including product, category, inventory, customer, order, payment, return/refund, reporting, and system management.

The platform must be designed so that the initial implementation can run as an academic/portfolio project while keeping a clear path to real deployment. The architecture should therefore separate frontend, backend business logic, data, infrastructure, security, integrations, and observability instead of treating the application as a simple collection of pages and API calls.

## 1.3 Core product decision

This project is NOT a marketplace in the current scope. There is no external seller registration and no seller-side product publishing. All products are controlled by the business through authorized Manager/Admin accounts.

# 2. System Scope

## 2.1 In scope

- Authentication and authorization
- User and customer management
- Product management
- Category management
- Inventory management
- Shopping cart
- Checkout
- Order management
- Payment integration
- Returns and refunds
- Notifications
- Analytics and management dashboard
- Security controls
- Audit logging
- Monitoring and observability
- Testing
- CI/CD and deployment
- Database backup and recovery readiness

## 2.2 Out of scope

- Marketplace seller registration
- Seller Center
- External seller product listing
- Seller commissions
- Seller payouts
- Seller verification
- Seller-to-customer direct management

# 3. Actors and Roles

## 3.1 Customer

Customers are end users who purchase products through the storefront.

- Register, login, logout
- Browse, search, filter, and sort products
- View product details
- Add, remove, and update cart items
- Checkout and make payment
- View and track their own orders
- Cancel eligible orders
- Request returns or refunds
- Manage personal profile and addresses
- Submit product reviews when the business rules allow it

Customers must not create, edit, delete, or publish products; change inventory; view other customers; view business-wide revenue; manage global orders; or access the administration dashboard.

## 3.2 Manager / Admin

Manager/Admin accounts operate the business side of the platform.

- Create, update, publish, unpublish, and archive products
- Manage categories
- Manage inventory and stock adjustments
- View and update orders
- Manage customers
- Review payments and refunds
- Process return requests
- View sales and operational analytics
- Manage system configuration
- View audit logs subject to authorization

## 3.3 Role-based authorization

The system must implement Role-Based Access Control (RBAC). A starting role model is CUSTOMER, MANAGER, and ADMIN. MANAGER and ADMIN may begin with similar operational capabilities, but the permission model must be explicit so roles can be separated later without redesigning the whole application.

# 4. Functional Requirements

## 4.1 Authentication and Authorization

Authentication shall support email/password as the primary method, with Google OAuth as an optional extension. The system shall distinguish authentication (who the user is) from authorization (what the user is allowed to do).

The authorization layer shall support permissions such as product:create, product:update, product:view, order:view, order:update, order:cancel, inventory:view, inventory:update, analytics:view, user:view, and user:update.

## 4.2 Product Management

Authorized managers shall manage the complete product lifecycle. A product should at minimum contain:

- id
- name
- slug
- description
- price
- compare_price
- sku
- stock reference
- status
- category_id
- brand
- thumbnail
- images
- created_at
- updated_at

Recommended product statuses: DRAFT, ACTIVE, OUT_OF_STOCK, INACTIVE, and ARCHIVED.

Business rules: a product cannot be published when required fields are missing; inventory quantities cannot become negative; customers cannot modify prices or stock.

## 4.3 Category Management

Managers shall create, edit, archive/delete according to business rules, and organize categories. The data model should support parent/child categories so the system can evolve from a flat catalog to a hierarchical catalog.

## 4.4 Product Image Management

Product images should be stored in object storage rather than directly as large binary objects in PostgreSQL. The backend stores image metadata such as image_url, storage_key, alt_text, and sort_order.

Frontend -> Upload API -> Object Storage -> Image URL/Metadata -> Database

## 4.5 Inventory Management

Inventory management must explicitly support available stock and reserved stock. A recommended model contains product_id, available_quantity, reserved_quantity, and updated_at, plus an inventory transaction/history table for traceability.

During checkout, stock must be validated and reserved before or as part of order/payment processing. Failed or abandoned payment attempts must release the reservation. Successful payment finalizes the inventory deduction.

## 4.6 Shopping Cart

Customers shall be able to add products, remove products, change quantities, view the cart, and clear the cart. Before checkout, the backend must revalidate product availability, current price, product status, and stock rather than trusting values calculated in the browser.

## 4.7 Checkout

Checkout must execute the following logical sequence:

1. Validate cart and product state
2. Validate stock
3. Calculate subtotal
4. Apply eligible discounts
5. Calculate shipping cost
6. Calculate total
7. Create a pending order
8. Reserve inventory
9. Initiate payment

The backend is the source of truth for pricing, totals, inventory, and order state.

## 4.8 Order Management

Orders must use a controlled state machine. Recommended core states are:

PENDING_PAYMENT -> PAID -> PROCESSING -> PACKED -> SHIPPED -> DELIVERED

Exception and after-sales states may include CANCELLED, RETURN_REQUESTED, RETURNED, and REFUNDED. State transitions must be validated on the backend and recorded in order_status_history.

## 4.9 Payment

Payment must be designed for a real provider integration rather than a frontend-only mock. The logical flow is:

Frontend -> Backend Payment Service -> Payment Gateway -> Webhook/IPN -> Backend -> Order/Payment Update

Supported providers may include VNPay, MoMo, Stripe, or another provider selected for deployment. Payment data should include payment_id, order_id, provider, transaction_id, amount, status, paid_at, and created_at. Recommended payment statuses are PENDING, SUCCESS, FAILED, CANCELLED, and REFUNDED.

## 4.10 Return and Refund

Customers may submit return/refund requests within business rules. A request can contain order_id, reason, description, and supporting images. Managers can approve, reject, request more information, and initiate refund processing. The system must preserve the complete request and decision history.

## 4.11 Customer Management

Authorized managers can search customers, inspect profiles and order history, and enable/disable accounts according to business policy. Passwords must never be exposed to administrators in plaintext.

## 4.12 Admin Dashboard and Analytics

The administration dashboard should provide operational KPIs such as total revenue, number of orders, number of customers, product count, low-stock products, pending orders, returns, and refund amounts. Additional reports may include revenue by day/week/month, order trends, top-selling products, average order value, and customer purchase frequency.

Dashboard pages should consume purpose-built analytics/reporting APIs rather than directly issuing many unrelated queries from the browser.

## 4.13 Analytics Events

The system should capture business events such as product_view, product_search, add_to_cart, remove_from_cart, checkout_started, payment_success, order_created, order_cancelled, and return_created. This creates a foundation for future BI or Data Science work without redesigning the core transaction architecture.

## 4.14 Notification

Important events such as account creation, order creation, payment result, shipment, delivery, return approval, and refund completion should be able to generate notifications via email, SMS, or push notification.

Notification work should not block critical customer requests unnecessarily. Where appropriate, events should be placed onto a queue and processed asynchronously.

## 4.15 Search

The storefront should support product search, filtering, sorting, pagination, and category/price/stock filters. PostgreSQL search may be sufficient for the first production iteration. Elasticsearch/OpenSearch can be introduced later if catalog size or search complexity requires it.

# 5. API Requirements

Backend endpoints should be grouped by business domain and protected by authorization rules.

- /api/auth
- /api/users
- /api/products
- /api/categories
- /api/cart
- /api/orders
- /api/payments
- /api/inventory
- /api/returns
- /api/admin
- /api/analytics

Example endpoint patterns: GET /api/products, GET /api/products/:id, POST /api/admin/products, PATCH /api/admin/products/:id, DELETE /api/admin/products/:id. The frontend must never bypass the backend to access the database directly.

# 6. Database Requirements

PostgreSQL is the recommended primary transactional database. The data model should be normalized enough to preserve integrity while keeping domain relationships clear.

Recommended main tables:

- users
- roles
- permissions
- user_roles
- products
- product_images
- categories
- inventory
- inventory_transactions
- carts
- cart_items
- orders
- order_items
- order_status_history
- payments
- refunds
- returns
- return_items
- reviews
- notifications
- analytics_events
- audit_logs

# 7. Target Architecture

The target solution should be modular, layered, and production-oriented. A practical first architecture is a modular backend rather than immediately adopting a distributed microservice system. Domains can be separated in code while running as one deployable backend. Specific high-load domains can be split later if necessary.

~~~text
Users
  |
  v
CDN / WAF
  |
  v
Load Balancer
  |
  +-------------------+----------------------+
  |                                          |
  v                                          v
Next.js Frontend                        Backend API
                                           |
              +----------------------------+------------------------------+
              |            |          |          |          |              |
              v            v          v          v          v              v
           Auth       Products     Orders    Payments  Inventory    Returns
              |            |          |          |          |              |
              +------------+----------+----------+----------+--------------+
                                           |
                         +-----------------+----------------+
                         |                 |                |
                         v                 v                v
                     PostgreSQL          Redis        Message Queue
                         |                                  |
                         v                                  v
                    Object Storage                     Notifications
                                                           Analytics
~~~

# 8. Frontend Requirements

The current Next.js application should evolve into a structured frontend rather than a flat collection of pages. The recommended layers are:

- Presentation layer: pages, layouts, reusable components
- State layer: React Context/Zustand for client state; React Query for server state where appropriate
- Service/API layer: authenticated API client and domain-specific service functions
- Route protection: middleware/route groups for customer and admin areas
- Environment configuration: public and server-only environment variables separated correctly

Suggested functional route groups include customer storefront routes and a separate admin route group. The admin UI must never rely on visual hiding alone for security; all permissions must be enforced by the backend.

# 9. Security Requirements

- HTTPS in deployed environments
- Secure password hashing
- JWT/session security
- Role-based authorization
- Input validation
- Rate limiting
- CORS policy
- CSRF protection where applicable
- SQL injection protection through parameterized ORM queries
- XSS protection and output encoding
- Secure cookies
- Environment variables and secret management
- Audit logging for sensitive administrative actions

No production secret, database credential, payment credential, JWT secret, or .env file should be committed to GitHub.

# 10. Infrastructure and Deployment

The system should be deployable using containers or another reproducible deployment approach. A recommended initial pipeline is:

Developer -> GitHub -> GitHub Actions -> Lint/Test/Build -> Docker Image -> Deploy -> Monitor

The production environment may be hosted on a VPS or a cloud platform such as AWS, GCP, or Azure. Static assets and images should be distributed through a CDN/object-storage strategy where appropriate.

The database must support migration, automated backup, and a documented recovery procedure.

# 11. Observability and Audit

Production operation requires application logs, access logs, error tracking, performance metrics, and health checks. OpenTelemetry, Prometheus/Grafana, Sentry, or an equivalent stack may be used.

Audit logs should record who performed a sensitive action, what changed, when it changed, and which resource was affected. For high-value changes, before/after values should be preserved where appropriate.

# 12. Testing Requirements

## 12.1 Unit tests

- Order calculation
- Price calculation
- Inventory reservation/release
- Permission logic
- Payment state transition

## 12.2 Integration tests

- Create order
- Checkout
- Payment callback/webhook
- Inventory update
- Return/refund workflow

## 12.3 End-to-end tests

Login -> Browse -> Add to Cart -> Checkout -> Payment -> View Order

# 13. Non-Functional Requirements

## 13.1 Performance

Common pages and lightweight APIs should respond quickly under expected production load. Expensive operations should be optimized through indexing, caching, pagination, and asynchronous processing when appropriate.

## 13.2 Scalability

The architecture should support growth from a small academic deployment to a larger real deployment without requiring a full rewrite. This is achieved by keeping domain boundaries clear and avoiding unnecessary coupling.

## 13.3 Availability

Production deployment should support health checks, safe restart, backups, and recovery procedures.

## 13.4 Maintainability

Backend and frontend code should be organized by domain/module with clear controller, service, data-access, validation, and integration responsibilities.

# 14. Development Roadmap

| Phase | Scope |
| --- | --- |
| Phase 1 - Foundation | Next.js frontend, backend API, PostgreSQL, authentication, RBAC, project structure |
| Phase 2 - Commerce Core | Product, category, cart, checkout, order, inventory |
| Phase 3 - Payment | Real payment gateway, webhook, payment status, refund |
| Phase 4 - Management | Admin dashboard, customer management, inventory management, reports |
| Phase 5 - Production | Docker, CI/CD, HTTPS, cloud/VPS deployment, backup, monitoring, logging |
| Phase 6 - Scale | Redis, queue, search engine, analytics pipeline, CDN, further performance improvements |

# 15. Definition of Done - Production Ready

- [ ] Customer registration/login works
- [ ] RBAC works and admin routes are protected
- [ ] Admin can manage products and categories
- [ ] Inventory logic works without negative stock/overselling caused by basic race conditions
- [ ] Cart and checkout work
- [ ] Order lifecycle is enforced
- [ ] Real payment integration or production-grade gateway integration is implemented
- [ ] Payment callback/webhook is validated
- [ ] Return/refund flow works
- [ ] Notifications work for key events
- [ ] Admin dashboard and reports work
- [ ] Database migrations are repeatable
- [ ] Validation and security controls are implemented
- [ ] Application logging and monitoring work
- [ ] Automated tests cover core business logic
- [ ] Docker build succeeds
- [ ] CI/CD pipeline is working
- [ ] Production deployment is repeatable
- [ ] Database backup exists and recovery has been considered/tested
- [ ] Secrets are managed securely

# 16. Project Definition

EcoGreen Commerce & Management Platform is a production-oriented single-business e-commerce system that connects customers with a centralized business management platform, where authorized managers exclusively manage products, inventory, orders, payments, customers, returns, and business analytics. The system is designed with a modular frontend-backend architecture, secure role-based access control, transactional order and inventory management, real payment integration, asynchronous processing, observability, automated deployment, and scalable infrastructure so that the application can evolve from an academic project into a deployable real-world system.

# 17. Architectural Decision Principles

- Frontend must not access the database directly.
- Frontend must not be the source of truth for payment success, order state, total price, or inventory.
- All sensitive permissions must be enforced on the backend.
- Use a modular monolith first; split into independent services only when scale or operational needs justify it.
- Prefer real business workflows and auditable state transitions over UI-only simulations.
- Design data models and events so future analytics/Data Science capabilities can be added without rewriting transaction logic.
- Keep the system deployable at each major phase rather than postponing deployment concerns until the end.
