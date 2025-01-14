package bobby.sfdc.prototype.rest;

import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import bobby.sfdc.prototype.oauth.AuthenticationException;
import bobby.sfdc.prototype.oauth.json.OAuthErrorResponse;

import com.google.gson.JsonSyntaxException;
import com.google.gson.Gson;

/**
 * Helper class that will execute REST API calls and unmarshal JSON 
 * results to Object form.
 * 
 * @author bobby.white
 *
 * @param <ResponseClassType>
 */
public class APIExecutor<ResponseClassType> {
	private static final Logger _logger = Logger.getLogger(APIExecutor.class.getName());
	
	private final Class<ResponseClassType> apiResponseClassType;

	private final String authToken;
	private String lastResponseBody = "";
	private int httpResultCode=0;
	
	/**
	 * Constructor which takes the expected Response Type Class so that it can instantiate them later
	 * @param clazz
	 */
	public APIExecutor(Class<ResponseClassType> clazz, String authToken) {
		this.apiResponseClassType= clazz;
		this.authToken=authToken;
	}
	
	/**
	 * @param client
	 * @param apiOperation The REST call to execute
	 * @param responseClass JavaClass which represents the expected JSON response from the API
	 * @return an instance of the responseClass or an exception
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws AuthenticationException
	 */
	private  ResponseClassType processAPIResponse(CloseableHttpClient client, HttpRequest apiOperation) throws IOException,
			ClientProtocolException, AuthenticationException {
		String rawJsonResponse=null;
		String uri=null;
		try {
			setAuthenticationHeader(apiOperation,this.authToken);

			HttpResponse response=null;
			if (apiOperation instanceof HttpGet) {
				response = client.execute((HttpGet) apiOperation);
				uri = ((HttpGet)apiOperation).getURI().toString();
			} else if (apiOperation instanceof HttpPost) {
				response = client.execute((HttpPost) apiOperation);
				uri = ((HttpPost)apiOperation).getURI().toString();
			} else if (apiOperation instanceof HttpPatch) {
				response = client.execute((HttpPatch) apiOperation);
				uri = ((HttpPatch)apiOperation).getURI().toString();
			} else if (apiOperation instanceof HttpPut) {
				response = client.execute((HttpPut) apiOperation);				
				uri = ((HttpPut)apiOperation).getURI().toString();
			}	
			
			int code = response.getStatusLine().getStatusCode();
			
			_logger.info(response.getStatusLine().toString());
			
			rawJsonResponse = EntityUtils.toString(response.getEntity());
			
			this.httpResultCode=code;
			this.setLastResponseBody(rawJsonResponse);

			
			if (code==200 || (code==201 && rawJsonResponse != null)) {

				try {
					ResponseClassType r =  (ResponseClassType) new Gson().fromJson(rawJsonResponse, apiResponseClassType);
					return r;

				} catch (JsonSyntaxException | IllegalStateException e) {
					if (isXML(rawJsonResponse)) {
						// Attempt to convert the XML to JSON and allow reparsing
						try {
							return parseFromXML(rawJsonResponse);
						} catch (JAXBException e1) {
							_logger.log(Level.SEVERE,"Unable to parse XML: " + e1.toString());
							return null;
						}
							
					} else {
						handleJsonSyntaxException(rawJsonResponse, e);
						return null;
					}
				}
			} else if (code==201) {
				// File Upload successful
				return null;

			} else {
				_logger.info(uri);
				// The call failed for some reason
				if (rawJsonResponse != null && rawJsonResponse.startsWith("{")) {
				    OAuthErrorResponse errorResponse = new Gson().fromJson(rawJsonResponse, OAuthErrorResponse.class);
				    if (errorResponse.getError() == null) {
				    	throw new AuthenticationException(rawJsonResponse); // Couldn't parse as a normal OAuth error
				    } else {
				    	throw new AuthenticationException(errorResponse);
				    }
				} else if (rawJsonResponse != null && rawJsonResponse.startsWith("[{")) {
				    throw new AuthenticationException(rawJsonResponse);
				} else {
				    throw new AuthenticationException(rawJsonResponse);
				}
			}
		} finally {		
			client.close();
		}
	}

	/**
	 * Attempt to convert a simple flat XML representation as a JSON encoded string
	 * @param xml
	 * @return json
	 * @throws JAXBException 
	 */
	@SuppressWarnings("unchecked")
	private ResponseClassType parseFromXML(String xml) throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(apiResponseClassType);
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		StringReader reader = new StringReader(xml);
		return (ResponseClassType) unmarshaller.unmarshal(reader);	
	}

	/** 
	 * Test to see if the Json Response is really encoded as XML
	 * 
	 * @param rawJsonResponse
	 * @return true if it appears to be XML
	 */
	private boolean isXML(String rawJsonResponse) {
		return rawJsonResponse != null && rawJsonResponse.startsWith("<");
	}

	public static void setAuthenticationHeader(HttpRequest apiOperation, String authToken) {
		apiOperation.addHeader("Authorization","Bearer "+ authToken);
	}

	
	/**
	 * @param client
	 * @param apiOperation HTTP-GET call to execute
	 * @param responseClass JavaClass which represents the expected JSON response from the API
	 * @return an instance of the responseClass or an exception
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws AuthenticationException
	 */
	public  ResponseClassType processAPIGetResponse(CloseableHttpClient client, HttpGet apiOperation) throws IOException,
			ClientProtocolException, AuthenticationException {
		return processAPIResponse(client,apiOperation);
	}
	
	/**
	 * @param client
	 * @param apiOperation HTTP-PATCH call to execute
	 * @param responseClass JavaClass which represents the expected JSON response from the API
	 * @return an instance of the responseClass or an exception
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws AuthenticationException
	 */
	public ResponseClassType processAPIPatchResponse(CloseableHttpClient client, HttpPatch patchJob) throws ClientProtocolException,
			IOException, AuthenticationException {
		return processAPIResponse(client,patchJob);
	}
	/**
	 * @param client
	 * @param apiOperation HTTP-POST call to execute
	 * @param responseClass JavaClass which represents the expected JSON response from the API
	 * @return an instance of the responseClass or an exception
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws AuthenticationException
	 */
	public ResponseClassType processAPIPostResponse(CloseableHttpClient client, HttpPost post) throws ClientProtocolException, IOException, AuthenticationException {
		return processAPIResponse(client,post);	
	}
	/**
	 * @param client
	 * @param apiOperation HTTP-POST call to execute
	 * @param responseClass JavaClass which represents the expected JSON response from the API
	 * @return an instance of the responseClass or an exception
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws AuthenticationException
	 */
	public ResponseClassType processAPIPostResponse(CloseableHttpClient client, HttpPut put) throws ClientProtocolException, IOException, AuthenticationException {
		return processAPIResponse(client,put);	
	}



	
	/**
	 * Convenience method to allow caller to reparse a result without repeating the server call
	 * 
	 * @param json a well formed json response
	 * @return an instance of the class
	 * @throws JsonSyntaxException
	 * @throws IllegalStateException
	 */
	public  ResponseClassType parseJsonBody(String json) throws JsonSyntaxException, IllegalStateException {
		if (json == null) throw new IllegalArgumentException("json parameter must not be null");
		ResponseClassType r =  (ResponseClassType) new Gson().fromJson(json, apiResponseClassType);
		return r;
	}
	
	/**
	 * 
	 * @param rawJsonResponse
	 * @param e an exception that occurred during parsing of the raw Json
	 */
	private void handleJsonSyntaxException(String rawJsonResponse,
			Throwable e) {
		String msg = e.getMessage();
		String extractExp = "\\d+$";
		
		if (msg.contains("line") && msg.contains("column")) {  // ends with 'at line xxx column yyyy'
			// if the message contains a location in the form:   line N column C   show the location in the original Json
			int index=0;
			Pattern p = Pattern.compile(extractExp);
			Matcher m = p.matcher(msg);

			if (m.find()) {
			   index=Integer.parseInt(m.group(0));
			   index = index >= 50 ? index-50 : index; // backup to show more context
			}
			_logger.log(Level.SEVERE,e.getMessage());
			_logger.log(Level.FINE,rawJsonResponse.substring(index));
			_logger.log(Level.FINER,rawJsonResponse);
			
		} else {
			_logger.log(Level.FINER,rawJsonResponse);
			_logger.log(Level.SEVERE,e.getMessage());
		}
		
		//saveToFile(JSON_ERRORFILE, rawJsonResponse);

	}

	/**
	 * @return the lastResponseBody
	 */
	public String getLastResponseBody() {
		return lastResponseBody;
	}

	/**
	 * @param lastResponseBody the lastResponseBody to set
	 */
	public void setLastResponseBody(String lastResponseBody) {
		this.lastResponseBody = lastResponseBody;
	}

	/**
	 * @return the httpResultCode
	 */
	public int getHttpResultCode() {
		return httpResultCode;
	}
}
