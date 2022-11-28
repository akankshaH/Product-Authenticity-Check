package com.amazonaws.samples;

import java.awt.image.BufferedImage;

//Java code to generate QR code

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class MyQr {

	// Function to create the QR code
	public BitMatrix createQR(String data,
								String charset, Map hashMap,
								int height, int width)
		throws WriterException, IOException
	{

		BitMatrix matrix = new MultiFormatWriter().encode(
			new String(data.getBytes(charset), charset),
			BarcodeFormat.QR_CODE, width, height);
		
		System.out.println("QR Code Generated!!! ");
		
		return matrix;
	}

	// Driver code
	public BufferedImage createNewQR(String data)
	{

		// Encoding charset
		String charset = "UTF-8";

		Map<EncodeHintType, ErrorCorrectionLevel> hashMap
			= new HashMap<EncodeHintType,
						ErrorCorrectionLevel>();

		hashMap.put(EncodeHintType.ERROR_CORRECTION,
					ErrorCorrectionLevel.L);

		// Create the QR code 
		BufferedImage image = new BufferedImage(200,200,BufferedImage.TYPE_BYTE_BINARY);
		try {
		BitMatrix matrix = createQR(data, charset, hashMap, 200, 200);
		image = MatrixToImageWriter.toBufferedImage(matrix);
		} catch (WriterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return image;
		
	}
}
