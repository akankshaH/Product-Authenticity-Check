package com.amazonaws.samples;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Iterator;


import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

	
public class SearchDB implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	private final String tableName = "QRdatabase";
	
//---------------------------------------connect to rds database-----------------------------------
	
	//retrieve secrets from secrets manager
	public static String[] getSecret() {
		
	     String secretName = "userDBsecret";
	     // Create a Secrets Manager client
	     AWSSecretsManager SMclient = AWSSecretsManagerClientBuilder.standard().build();
	     GetSecretValueResult getSecretValueResult = SMclient.getSecretValue(new GetSecretValueRequest().withSecretId(secretName));
	     String secret = getSecretValueResult.getSecretString();
	     
	     JsonObject jsonObject = JsonParser.parseString(secret).getAsJsonObject();
	     //System.out.println(secret);
	     String dbUsername = jsonObject.get("username").getAsString();
	     String dbPassword = jsonObject.get("password").getAsString();
	     String dbHost = jsonObject.get("host").getAsString();
	     String secretArray[] = {dbUsername,dbPassword,dbHost}; 
	     
	     return secretArray;
	 }
	
	
	//connect to rds database
	static Connection connection;
    static {
    	String secretArray[] = getSecret();
        try {
            String username = secretArray[0];
            String password = secretArray[1];
            String host = secretArray[2];
            String port = "3306";
            String dbName = "userDetails";
            String URL = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?user=" + username + "&password=" + password;
            //System.out.println(URL);
            connection = DriverManager.getConnection(URL);
        } catch (SQLException e) { 
			e.printStackTrace();
        }
        if(connection == null)
        	System.out.println("connection not established");
        else
        	System.out.println("connection established");
    }
    
	 
    
//-------------------------------------------------------------------------------
	

	
	@Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        
		final LambdaLogger logger = context.getLogger();
        //logger.log(input.getBody());
        
        //process the data
        Gson gson = new Gson();
        APIuser bodyInput = gson.fromJson(input.getBody(), APIuser.class);
        
        //Decode the id_token to get email and username of the user
        String token_id  = bodyInput.getToken_id();
        String[] chunks = token_id.split("\\.");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        //String header = new String(decoder.decode(chunks[0]));
        String payload = new String(decoder.decode(chunks[1]));		//contains user details like email and username
        JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();
        String userEmail = jsonObject.get("email").getAsString();
        String username = jsonObject.get("cognito:username").getAsString();

        
        String serialNo  = bodyInput.getSerialNo();
        Table table = dynamoDB.getTable(tableName);
        //Query the table for the serial number
        QuerySpec spec = new QuerySpec()
        	    .withKeyConditionExpression("SerialNo = :serialNo")
        	    .withValueMap(new ValueMap()
        	        .withString(":serialNo", serialNo));

    	ItemCollection<QueryOutcome> items = table.query(spec);
    	
    	String productName = "";
        String productType = "";
        String color = "";
        String size = "";
        
    	//logger.log(items.toString());
    	Iterator<Item> iterator = items.iterator();
    	Item item = null;
    	while (iterator.hasNext()) {
    	    item = iterator.next();
    	    productName = item.getString("ProductName");
            productType = item.getString("ProductType");
            color = item.getString("Color");
            size = item.getString("Size");
    	}
    	
    	
    	String email_subject = "";
    	String email_message = "";
    	
    	if(item == null) {
    		email_subject = "The product is FAKE!";
    		email_message =  "To buy authentic Nike shoes please visit our official website";   		
    	}
    	else {
    		email_subject = "The product is AUTHENTIC!";
        	email_message = "The product details are as follows:\n" +
        					"ProductName: " + productName + "\n" +
        					"ProductType: " + productType + "\n" +
        					"Color: " + color + "\n" +
        					"Size: " + size + "\n" ;    		
    	}
    	
    	
    	String FROM = "awsses2@gmail.com";
    	AmazonSimpleEmailService sesClient = AmazonSimpleEmailServiceClientBuilder.standard().build();
    	SendEmailRequest emailRequest = new SendEmailRequest()
    	          .withDestination(new Destination().withToAddresses(userEmail))
    	          .withMessage(new Message().withBody(new Body()
    	                  .withText(new Content().withData(email_message)))
    	              .withSubject(new Content().withData(email_subject)))
    	          .withSource(FROM);
    	sesClient.sendEmail(emailRequest);
    	
//----------------insert data into rds---------------------------------    	
		Statement statement;
		try {
			statement = connection.createStatement();
			String query = "INSERT INTO users(email, username) VALUES ('" + userEmail + "','" + username + "')";
			int responseCode = statement.executeUpdate(query);
			if(1 == responseCode)
			{
				logger.log("Successfully updated details");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//--------------------------------------------------------------------------
  	
		
    	APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        return response
                .withStatusCode(200)
                .withBody("Product detalis for the serial number " + serialNo + " are: " + email_message );
        	
	} 

}
