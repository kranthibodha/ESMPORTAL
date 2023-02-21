package com.sap.cap.esmapi.utilities.pojos;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TY_Case_SrvCloud
{
    private String subject;    
    private String caseType;
    private TY_Account_CaseCreate account;
    private TY_CatgLvl1_CaseCreate categoryLevel1;
    private TY_CatgLvl1_CaseCreate categoryLevel2;
    private TY_Description_CaseCreate description;
    
    @Override
    public String toString() {
        return "TY_Case_SrvCloud [account=" + account + ", caseType=" + caseType + ", categoryLevel1=" + categoryLevel1
                + ", categoryLevel2=" + categoryLevel2 + ", description=" + description + ", subject=" + subject + "]";
    }

    

}
