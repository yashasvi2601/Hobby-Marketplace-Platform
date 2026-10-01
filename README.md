# Hobby Marketplace Platform

A full-stack web app that connects people looking for a new hobby with local
businesses offering activities (classes, workshops, meetups, etc.). Users take
a short quiz to get matched with activities near them, and business owners can
list and manage their own offers.

## How it works

- **App clients** (regular users) sign up, take a short multiple-choice quiz
  (interests + location), and get a set of hobby suggestions matched against
  their answers. They can also browse all listings and save favorites.
- **Business owners** sign up separately, and can create, edit, and delete
  hobby listings (name, description, price, category, location, photos,
  contact info).
- Matching is rule-based: listings are filtered by the user's selected
  location, then checked against the categories picked in the quiz. There's
  no ML/AI involved — it's a straightforward filter over the data.
- Authentication is JWT-based, with three roles: `USER`, `BUSINESS_USER`, and
  `ADMIN`.

## Tech stack

**Frontend** — `react-frontend/`
- React 17 + Ionic React components
- React Router, Formik + Yup for forms/validation
- Axios for API calls (with auto token refresh)

**Backend** — `spring-backend/`
- Spring Boot 2.4 (Java 17)
- Spring Data JPA / Hibernate, PostgreSQL
- Spring Security + JWT
- Cloudinary for image uploads, Spring Mail for notifications
- springdoc-openapi / Swagger UI for API docs

## Project structure

```
react-frontend/   React SPA (runs on :4200)
spring-backend/   Spring Boot REST API (runs on :8080)
```

## Running locally

### Prerequisites
- Node.js 16+ and npm
- Java 17
- A local PostgreSQL instance

### 1. Database
Create a database and update the connection details in
`spring-backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/hobbie_backend_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

`spring.jpa.hibernate.ddl-auto=update` is set, so the schema is created/updated
automatically on startup — no manual migrations needed.

### 2. Backend

```bash
cd spring-backend
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`. On first startup, the app seeds:
- Demo logins: `user` / `topsecret` (app client) and `business` / `topsecret`
  (business owner)
- All hobby categories and locations
- A handful of sample hobby listings under the `business` account

API docs are available at `http://localhost:8080/swagger-ui/index.html`.

### 3. Frontend

```bash
cd react-frontend
npm install
npm start
```

Runs on `http://localhost:4200` and talks to the backend on `:8080`.

### Optional: Email notifications
Password-reset emails require valid `spring.mail.username` /
`spring.mail.password` values in `application.properties`. Without them,
the rest of the app still works — only the notification endpoint will fail.
