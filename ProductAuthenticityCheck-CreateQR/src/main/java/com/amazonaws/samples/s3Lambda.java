package com.amazonaws.samples;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.gson.Gson;


public class s3Lambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	private final String tableName = "QRdatabase";
	String dstBucket = 	"qrbucket-11022022";
	
	public String generateQRCode(String data) throws IOException {

		MyQr generateQR = new MyQr();
		BufferedImage image = generateQR.createNewQR(data);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(image, "png", os);
		byte[] buffer = os.toByteArray();
		InputStream is = new ByteArrayInputStream(buffer);
		
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
		ObjectMetadata meta = new ObjectMetadata();
		meta.setContentLength(buffer.length);
		meta.setContentType("image/png");
		String objectKey = data + ".png";
		
		s3Client.putObject(dstBucket, objectKey, is, meta);
		
		// Set the presigned URL to expire after 60 minutes.
        java.util.Date expiration = new java.util.Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 10;
        expiration.setTime(expTimeMillis);

        // Generate the presigned URL.
        GeneratePresignedUrlRequest generatePresignedUrlRequest = 
                new GeneratePresignedUrlRequest(dstBucket, objectKey)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);
        String url = s3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
        
		return url;
	}

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        final LambdaLogger logger = context.getLogger();
        logger.log(input.getBody());
        
        //process the data
        Gson gson = new Gson();
        APIresponse bodyInput = gson.fromJson(input.getBody(), APIresponse.class);
        
        String serialNo  = bodyInput.getSerialNo();
        String productName = bodyInput.getProductName();
   	
        Table table = dynamoDB.getTable(tableName);
        
        logger.log(serialNo + " " + productName +"\n");		//log the data for checks
        
        String url="";
		try {
			url = generateQRCode(serialNo);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        //put records in dynamo db
        Item item = new Item().withPrimaryKey("SerialNo", serialNo).
        		withString("QR_Code", url).
        		withString("ProductName", productName).
        		withString("ProductType", bodyInput.getProductType()).
        		withString("Color", bodyInput.getColor()).
        		withString("Size", bodyInput.getSize());
        
        table.putItem(item);
        
        //logger.log(url + "\n");		
        
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(responseHeaders);
        return response
                .withStatusCode(200)
                .withBody("URL of the QR code generated: "+url);   
    }
    
}   

