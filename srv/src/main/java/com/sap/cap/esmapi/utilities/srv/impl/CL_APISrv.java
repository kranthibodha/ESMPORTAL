package com.sap.cap.esmapi.utilities.srv.impl;

import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.utilities.pojos.TY_SrvCloudUrls;
import com.sap.cap.esmapi.utilities.srv.intf.IF_APISrv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;

import java.io.IOException;
import java.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;


@Service
@Scope("prototype")
public class CL_APISrv implements IF_APISrv
{


    @Autowired
    private TY_SrvCloudUrls srvCloudUrls;
    
    @Override
    public long getNumberofEntitiesByUrl(String url) throws RuntimeException, IOException
    {
        
        long numEmtities = 0;
        JsonNode jsonNode = null;

        HttpResponse response = null;
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();

        try 
        {
            System.out.println("Getting Entities for Url : " + url);    
            String encoding = Base64.getEncoder().encodeToString((srvCloudUrls.getUserName() + ":" + srvCloudUrls.getPassword()).getBytes());
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
            httpGet.addHeader("accept", "application/json");
            //Fire the Url
            try 
            {
                response = httpClient.execute(httpGet);
                // verify the valid error code first
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) 
                {
                    throw new RuntimeException("Failed with HTTP error code : " + statusCode);
                }

                //Try and Get Entity from Response
                HttpEntity entity = response.getEntity();
                String apiOutput = EntityUtils.toString(entity);
                //Lets see what we got from API
                //System.out.println(apiOutput);

                //Conerting to JSON
                ObjectMapper mapper = new ObjectMapper();
                jsonNode = mapper.readTree(apiOutput);
                if(jsonNode != null)
                {
                    JsonNode countsNode = jsonNode.path("count");
                    if(countsNode != null)
                    {
                        System.out.println("Count node Bound!!");
                        numEmtities = countsNode.longValue();
                        System.out.println("# of entities : " + numEmtities);
                    }
                }

            } 
            catch (IOException e) 
            {
                
                e.printStackTrace();
            }
          
        }

        finally
        {
            httpClient.close();
        }


        return numEmtities;
    
    }
    
}
