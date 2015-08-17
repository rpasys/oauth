package de.adorsys.oauth.server;

import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityContextAssociation;
import org.jboss.security.SubjectInfo;
import org.jboss.security.identity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationErrorResponse;
import com.nimbusds.oauth2.sdk.AuthorizationRequest;
import com.nimbusds.oauth2.sdk.AuthorizationSuccessResponse;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;

import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;


/**
 * AuthzResource
 */
@SuppressWarnings("unused")
@Path("auth")
@ApplicationScoped
public class AuthResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);

    @Context
    private HttpServletRequest servletRequest;

    @Context
    private ServletContext servletContext;

    @Inject
    private TokenStore tokenStore;
    
    @Inject
    private Principal principal;

    private long tokenLifetime;

    @PostConstruct
    public void postConstruct() {
        try {
            tokenLifetime = Long.valueOf(servletContext.getInitParameter("lifetime"));
        } catch (Exception e) {
            tokenLifetime = 8 * 3600;
        }

        LOG.info("token lifetime {}", tokenLifetime);
    }


    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response authorizePost() throws Exception {

        AuthorizationRequest request = AuthorizationRequest.parse(ServletUtils.createHTTPRequest(servletRequest));
        LOG.info("AuthorizationRequest {}", request.toQueryString());
        
        ResponseBuilder response = Response.status(302).header("Authorization", null); // remove existing auth ...
        
        if (request.getRedirectionURI() == null) {
            return response.location(
                    new AuthorizationErrorResponse(request.getEndpointURI(), OAuth2Error.INVALID_REQUEST, request.getState(), request.getResponseMode()).toURI())
                    .build();
        }

        if (servletRequest.getUserPrincipal() == null) {
            return response.location(
                    new AuthorizationErrorResponse(request.getRedirectionURI(), OAuth2Error.UNAUTHORIZED_CLIENT, request.getState(), request.getResponseMode()).toURI())
                    .build();
        }

        if (request.getClientID() == null) {
            return response.location(
                    new AuthorizationErrorResponse(request.getRedirectionURI(), OAuth2Error.INVALID_CLIENT, request.getState(), request.getResponseMode()).toURI())
                    .build();
        }

        if (request.getResponseType() == null) {
            return response.location(
                    new AuthorizationErrorResponse(request.getRedirectionURI(), OAuth2Error.UNSUPPORTED_RESPONSE_TYPE, request.getState(), request.getResponseMode()).toURI())
                    .build();
        }

        UserInfo userInfo = createUserInfo();
        LOG.debug(userInfo.toJSONObject().toJSONString());
        
        BearerAccessToken accessToken = new BearerAccessToken(tokenLifetime, request.getScope());

        if (request.getResponseType().impliesCodeFlow()) {
            AuthorizationCode authCode = new AuthorizationCode();
            LOG.info("impliesCodeFlow {}", authCode.toJSONString());
            
            tokenStore.add(accessToken, userInfo, authCode);
            return response.location(new AuthorizationSuccessResponse(request.getRedirectionURI(), authCode, null, request.getState(), request.getResponseMode()).toURI()).build();
        }

        LOG.info("impliesTokenFlow {}", accessToken.toJSONString());
        tokenStore.add(accessToken, userInfo);

        AuthorizationSuccessResponse successResponse = new AuthorizationSuccessResponse(request.getRedirectionURI(), null, accessToken, request.getState(), request.getResponseMode());
        String location = successResponse.toURI().toString();
        LOG.info("location {}", location);

        return response.location(new URI(location)).build();
    }

    @GET
    public Response authorizeGet() throws Exception {
        return authorizePost();

    }
    
    private UserInfo createUserInfo() {

        SecurityContext context = SecurityContextAssociation.getSecurityContext();
        SubjectInfo subjectInfo = context.getSubjectInfo();
        String name = principal.getName();

        List<String> roles = new ArrayList<>();
        UserInfo userInfo = new UserInfo(new Subject(name));
        userInfo.setName(name);

        if (subjectInfo.getRoles() != null) {
            for (Role role : subjectInfo.getRoles().getRoles()) {
                roles.add(role.getRoleName());
            }
            userInfo.setClaim("groups", roles);
        }
        return userInfo;
    }



}
