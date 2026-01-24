# API Path Organization Guide

## Current Configuration

**Removed**: `quarkus.rest.path=/api`  
**Benefit**: Full control over API paths using `@Path` annotations

## Why This Approach is Better

### ✅ Flexibility
You can organize different APIs with different base paths:
```java
@Path("/api/v1")     // Version 1 API
@Path("/api/v2")     // Version 2 API
@Path("/admin")      // Admin endpoints
@Path("/public")     // Public endpoints
@Path("/webhooks")   // Webhook endpoints
```

### ✅ Clear and Explicit
Each resource class explicitly declares its full path - easier to understand and maintain.

### ✅ No Hidden Configuration
Developers can see the full path directly in the code without checking `application.properties`.

## Current API Structure

### FileUploadResource
**Path**: `/api`  
**Full URLs** (with `/whisper` root):
- `POST /whisper/api/validate-passphrase`
- `POST /whisper/api/upload`

```java
@Path("/api")
public class FileUploadResource {
    @POST
    @Path("/validate-passphrase")
    public Response validatePassphrase(...) { }
    
    @POST
    @Path("/upload")
    public Response uploadFile(...) { }
}
```

### GreetingResource
**Path**: `/hello`  
**Full URL**: `GET /whisper/hello`

```java
@Path("/hello")
public class GreetingResource {
    @GET
    public String hello() { }
}
```

## Examples for Future Features

### Example 1: API Versioning
```java
// V1 API
@Path("/api/v1/users")
public class UserResourceV1 {
    @GET
    public List<User> getUsers() { }
}

// V2 API with breaking changes
@Path("/api/v2/users")
public class UserResourceV2 {
    @GET
    public List<UserDTO> getUsers() { }
}
```

**URLs**:
- `/whisper/api/v1/users`
- `/whisper/api/v2/users`

### Example 2: Different Domains
```java
// Public API
@Path("/api/public")
public class PublicApiResource {
    @GET
    @Path("/status")
    public Status getStatus() { }
}

// Admin API
@Path("/api/admin")
public class AdminApiResource {
    @POST
    @Path("/users")
    public Response createUser() { }
}

// Integration API
@Path("/integration")
public class IntegrationResource {
    @POST
    @Path("/callback")
    public Response handleCallback() { }
}
```

**URLs**:
- `/whisper/api/public/status`
- `/whisper/api/admin/users`
- `/whisper/integration/callback`

### Example 3: Feature-Based Organization
```java
// Authentication
@Path("/auth")
public class AuthResource {
    @POST
    @Path("/login")
    public Response login() { }
    
    @POST
    @Path("/logout")
    public Response logout() { }
}

// File Management
@Path("/files")
public class FileResource {
    @GET
    public List<File> listFiles() { }
    
    @POST
    @Path("/upload")
    public Response upload() { }
}

// User Management
@Path("/users")
public class UserResource {
    @GET
    public List<User> getUsers() { }
    
    @POST
    public Response createUser() { }
}
```

**URLs**:
- `/whisper/auth/login`
- `/whisper/auth/logout`
- `/whisper/files`
- `/whisper/files/upload`
- `/whisper/users`

## Best Practices

### 1. Use Consistent Naming
```java
✅ @Path("/api/users")
✅ @Path("/api/files")
❌ @Path("/api/user")  // Singular
❌ @Path("/API/Files")  // Inconsistent casing
```

### 2. Group Related Endpoints
```java
@Path("/api/mp3")
public class Mp3Resource {
    @POST
    @Path("/upload")       // POST /whisper/api/mp3/upload
    
    @GET
    @Path("/{id}")         // GET /whisper/api/mp3/{id}
    
    @DELETE
    @Path("/{id}")         // DELETE /whisper/api/mp3/{id}
}
```

### 3. Version Your APIs When Needed
```java
@Path("/api/v1/resource")  // Stable, production
@Path("/api/v2/resource")  // New version with changes
@Path("/api/beta/resource") // Experimental features
```

### 4. Separate Public and Private APIs
```java
@Path("/api/public")   // No authentication
@Path("/api/private")  // Requires authentication
@Path("/api/admin")    // Requires admin role
```

## Migration Example

If you want to add a new feature (e.g., user management):

```java
package me.cresterida;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/users")
public class UserResource {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers() {
        // List all users
        return Response.ok().build();
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(UserRequest request) {
        // Create a new user
        return Response.ok().build();
    }
    
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser(@PathParam("id") String id) {
        // Get specific user
        return Response.ok().build();
    }
}
```

**Result**: New endpoints at:
- `GET /whisper/api/users`
- `POST /whisper/api/users`
- `GET /whisper/api/users/{id}`

**No configuration changes needed!** Just create the new class and deploy.

## Summary

✅ **Removed**: `quarkus.rest.path=/api`  
✅ **Benefit**: Full control over API organization  
✅ **Current Structure**: Working perfectly with `/api` prefix  
✅ **Future**: Easy to add new features with any path structure you want  

Your application is now more flexible and easier to extend!
