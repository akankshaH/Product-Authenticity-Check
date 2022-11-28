# Product-Authenticity-Check
Product Authenticity Check is a project to help luxurious brands deal with the issue of duplicate products in the market.
The manufacturer can tag each product with a unique serial number. A QR code is created for this serial number. A customer can then validate the product by scanning the QR and entering the serial number in the web application. The presence of the serial number is checked in the product database and the product details are displayed. 
AWS services have been used to implement this.

## Architecture Diagram
<img width="959" alt="PAC_ArchitectureDiagram" src="https://user-images.githubusercontent.com/43901836/204223522-66568d89-8449-40c6-90a1-ef6ac8eea1dc.png">

## Description
#### CreateQR
Manufacturer enters product detalis and serial no. Create QR code and generate presigned url for it. Store details in dynamoDB (product details).

#### SearchDB
User enters serial no. Query database (product details) and send approprite messages for when serial number is found or not found as authentic or fake.
SES is used for sending emails. 

#### MarketingLambda
Send out marketing emails to all users that have subscribed to an SNS topic.

