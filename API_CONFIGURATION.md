# API Configuration Guide

## How to Change the Backend API URL

The application uses a centralized API service that reads the backend URL from environment configuration files. This makes it easy to change the API endpoint for different environments.

### Configuration Files

1. **Development**: `src/main/webui/src/environments/environment.ts`
2. **Production**: `src/main/webui/src/environments/environment.prod.ts`

### Current Configuration

#### Default (Quinoa - Served by Quarkus)
```typescript
apiUrl: '/api'
```
This uses relative URLs, which work when Angular is served by Quarkus through Quinoa on `http://localhost:8080`.

#### Standalone Angular (Development)
If you want to run `ng serve` independently on port 4200:
```typescript
apiUrl: 'http://localhost:8080/api'
```

#### Production
For production deployment:
```typescript
apiUrl: 'https://your-domain.com/api'
```

### How to Change

1. Open the environment file you want to modify:
   - Development: `src/main/webui/src/environments/environment.ts`
   - Production: `src/main/webui/src/environments/environment.prod.ts`

2. Update the `apiUrl` value:
   ```typescript
   export const environment = {
     production: false,
     apiUrl: 'YOUR_NEW_URL_HERE',
   };
   ```

3. Restart the development server or rebuild for the changes to take effect.

### API Endpoints

The following endpoints are available:

- **POST** `/whisper/api/validate-passphrase` - Validates user passphrase
  ```json
  Request: { "passphrase": "your-secret" }
  Response: { "validated": true }
  ```

- **POST** `/whisper/api/upload` - Uploads MP3 file
  ```
  Form Data:
  - file: File
  - email: string
  
  Response: { "message": "...", "fileUrl": "..." }
  ```

### Examples

#### Example 1: Change to Different Port
```typescript
apiUrl: 'http://localhost:9090/whisper/api'
```

#### Example 2: Change to Different Host
```typescript
apiUrl: 'http://192.168.1.100:8080/whisper/api'
```

#### Example 3: Production with HTTPS
```typescript
apiUrl: 'https://api.myapp.com/whisper/api'
```

### Testing Changes

After changing the API URL:

1. Restart the dev server
2. Open browser console (F12)
3. Check the Network tab to verify requests are going to the correct URL
4. The console will show any CORS or connection errors

### Troubleshooting

**Issue**: CORS errors when using full URL
- **Solution**: Ensure your Quarkus backend has CORS configured in `application.properties`:
  ```properties
  quarkus.http.cors=true
  quarkus.http.cors.origins=http://localhost:4200
  ```

**Issue**: 404 errors
- **Solution**: Verify the backend is running and the URL is correct

**Issue**: Changes not taking effect
- **Solution**: Clear browser cache or do a hard refresh (Ctrl+Shift+R)
