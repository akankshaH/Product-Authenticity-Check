package com.amazonaws.authenticitycheck;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

	
public class MarketingSNS implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	
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
        logger.log(input.getBody());
        
        //process the data
        Gson gson = new Gson();
        APIrequest bodyInput = gson.fromJson(input.getBody(), APIrequest.class);
        String message  = bodyInput.getMessage();

    	String email_subject = "Product Authenticity Check";
    	String email_message =  message;   		  	
        String topic_arn_email = "arn:aws:sns:us-east-1:949777409594:Product_Authenticity_topic";
    	AmazonSNS snsClient = (AmazonSNSClient) AmazonSNSClientBuilder.standard().build();   	
    	
//----------------fetch data from rds---------------------------------    	
		Statement statement;
		try {
	    	
			statement = connection.createStatement();
			String query = "SELECT email FROM userDetails.users "
					+ "WHERE  NOT EXISTS "
					+ "(SELECT * FROM userDetails.users WHERE snsFlag = 1)";
			
	    	ResultSet set = statement.executeQuery(query);    
	    		    	
	    	while (set.next()) {
	    		String email = set.getString("email");
	    		System.out.println(email);
	    		snsClient.subscribe(topic_arn_email, "email", email);
				String querySetFlag = "UPDATE userDetails.users SET snsFlag = " + (int)1 + " WHERE (email = \"" + email + "\")";				

				int responseCode = statement.executeUpdate(querySetFlag);
				if(1 == responseCode)
				{
					logger.log("Successfully updated flag");
				}	    	}
		} catch (SQLException e) {
			e.printStackTrace();	
		}
//-------------------------------------------------------------------------------------------
		
    	snsClient.publish(topic_arn_email, email_message, email_subject);   

    	APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        return response.withStatusCode(200);
	} 

}
