package com.sap.cap.esmapi.ui.pojos;

import java.sql.Timestamp;

import com.sap.cap.esmapi.utilities.pojos.TY_AttachmentResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TY_CaseFormAsync
{
    private TY_Case_Form caseForm;
    private String submGuid;
    private String userId;
    private Timestamp timestamp;
    private boolean valid; // To be set post Payload Validation
    private String[] catTreeSelCatg;
    private TY_AttachmentResponse attR;

}
