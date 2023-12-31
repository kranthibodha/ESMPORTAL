package com.sap.cap.esmapi.utilities.srv.impl;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.annotation.SessionScope;

import com.sap.cap.esmapi.catg.pojos.TY_CatalogItem;
import com.sap.cap.esmapi.catg.pojos.TY_CatalogTree;
import com.sap.cap.esmapi.catg.pojos.TY_CatgCus;
import com.sap.cap.esmapi.catg.pojos.TY_CatgCusItem;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatalogSrv;
import com.sap.cap.esmapi.events.event.EV_LogMessage;
import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.hana.logging.srv.intf.IF_HANALoggingSrv;
import com.sap.cap.esmapi.ui.pojos.TY_Attachment;
import com.sap.cap.esmapi.ui.pojos.TY_CaseEdit_Form;
import com.sap.cap.esmapi.ui.pojos.TY_CaseFormAsync;
import com.sap.cap.esmapi.ui.pojos.TY_Case_Form;
import com.sap.cap.esmapi.ui.srv.intf.IF_ESS_UISrv;
import com.sap.cap.esmapi.utilities.constants.GC_Constants;
import com.sap.cap.esmapi.utilities.enums.EnumCaseTypes;
import com.sap.cap.esmapi.utilities.enums.EnumMessageType;
import com.sap.cap.esmapi.utilities.enums.EnumStatus;
import com.sap.cap.esmapi.utilities.pojos.TY_CaseDetails;
import com.sap.cap.esmapi.utilities.pojos.TY_CaseESS;
import com.sap.cap.esmapi.utilities.pojos.TY_Message;
import com.sap.cap.esmapi.utilities.pojos.TY_NotesDetails;
import com.sap.cap.esmapi.utilities.pojos.TY_RLConfig;
import com.sap.cap.esmapi.utilities.pojos.TY_UserDetails;
import com.sap.cap.esmapi.utilities.pojos.TY_UserSessionInfo;
import com.sap.cap.esmapi.utilities.pojos.Ty_UserAccountContactEmployee;
import com.sap.cap.esmapi.utilities.srv.intf.IF_UserSessionSrv;
import com.sap.cap.esmapi.utilities.srvCloudApi.srv.intf.IF_SrvCloudAPI;
import com.sap.cap.esmapi.vhelps.pojos.TY_KeyValue;
import com.sap.cap.esmapi.vhelps.srv.intf.IF_VHelpLOBUIModelSrv;
import com.sap.cds.services.request.UserInfo;
import com.sap.cloud.security.xsuaa.token.Token;

import cds.gen.db.esmlogs.Esmappmsglog;
import lombok.extern.slf4j.Slf4j;

@Service
@SessionScope
@Slf4j
public class CL_UserSessionSrv implements IF_UserSessionSrv
{

    @Autowired
    private MessageSource msgSrc;

    @Autowired
    private UserInfo userInfo;

    @Autowired
    private IF_SrvCloudAPI srvCloudApiSrv;

    @Autowired
    private IF_ESS_UISrv essSrv;

    @Autowired
    private TY_RLConfig rlConfig;

    @Autowired
    private TY_CatgCus catgCusSrv;

    @Autowired
    private IF_CatalogSrv catalogSrv;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private IF_VHelpLOBUIModelSrv vHlpModelSrv;

    @Autowired
    private IF_HANALoggingSrv hanaLogSrv;

    // Properties

    private TY_UserSessionInfo userSessInfo;

    @Override
    public TY_UserDetails getUserDetails(Token token) throws EX_ESMAPI
    {
        // Token Blank
        if (token == null)
        {
            log.error(msgSrc.getMessage("NO_TOKEN", null, Locale.ENGLISH));
            throw new EX_ESMAPI(msgSrc.getMessage("NO_TOKEN", null, Locale.ENGLISH));

        }
        else
        {
            // Unauthenticated User
            if (!userInfo.isAuthenticated())
            {
                log.error(msgSrc.getMessage("UNAUTHENTICATED_ACCESS", new Object[]
                { token.getLogonName() }, Locale.ENGLISH));
                throw new EX_ESMAPI(msgSrc.getMessage("UNAUTHENTICATED_ACCESS", new Object[]
                { token.getLogonName() }, Locale.ENGLISH));
            }

            else
            {
                // #AUTH
                // Role Checks to be explicitly handled here
                if (CollectionUtils.isNotEmpty(userInfo.getRoles()))
                {
                    // Explicit Role Check for Interals and Externals and error in case of
                    // unassigned Role
                }

                if (userSessInfo == null)
                {
                    log.info("User Session Info. Instantiated!");
                    userSessInfo = new TY_UserSessionInfo();
                }

                // Return from Session if Populated else make some effort
                if (userSessInfo.getUserDetails() == null)
                {
                    // Fetch and Return
                    TY_UserDetails userDetails = new TY_UserDetails();
                    log.info("Fetching Logged in User Details!!");
                    userDetails.setAuthenticated(true);
                    userDetails.setRoles(userInfo.getRoles().stream().collect(Collectors.toList()));
                    Ty_UserAccountContactEmployee usAccConEmpl = new Ty_UserAccountContactEmployee();
                    usAccConEmpl.setUserId(token.getLogonName());
                    usAccConEmpl.setUserName(token.getGivenName() + " " + token.getFamilyName());
                    usAccConEmpl.setUserEmail(token.getEmail());
                    usAccConEmpl.setAccountId(srvCloudApiSrv.getAccountIdByUserEmail(usAccConEmpl.getUserEmail()));
                    usAccConEmpl
                            .setContactId(srvCloudApiSrv.getContactPersonIdByUserEmail(usAccConEmpl.getUserEmail()));

                    // Only seek Employee If Account/Contact not Found
                    if (!StringUtils.hasText(usAccConEmpl.getAccountId()))
                    {
                        // Seek Employee and populate
                        /* Enable once ESM Module is live and tested */
                        // usAccConEmpl.setEmployeeId(srvCloudApiSrv.getEmployeeIdByUserId(usAccConEmpl.getUserId()));
                        // usAccConEmpl.setEmployee(true);
                        /* Enable once ESM Module is live and tested */

                        // For Now Always Create an Account -This PART needs to be commented once ESM
                        // module is live
                        userDetails.setUsAcConEmpl(usAccConEmpl);
                        userSessInfo.setUserDetails(userDetails); // Set in Session
                        String accountID = this.createAccount();
                        userSessInfo.getUserDetails().getUsAcConEmpl().setAccountId(accountID);
                        // For Now Always Create an Account -This PART needs to be commented once ESM
                        // module is live

                    }
                    else
                    {
                        userDetails.setUsAcConEmpl(usAccConEmpl);
                        userSessInfo.setUserDetails(userDetails); // Set in Session
                    }
                    log.info("User Details populated in Session : "
                            + userSessInfo.getUserDetails().getUsAcConEmpl().toString());

                }
            }

        }

        return userSessInfo.getUserDetails();
    }

    @Override
    public TY_UserSessionInfo getESSDetails(Token token, boolean refresh) throws EX_ESMAPI
    {
        // Token must be present
        if (token != null)
        {
            // get User Details with Token
            getUserDetails(token);

            // Reload Cases if Refresh Requested or Cases List Blank
            if (refresh || CollectionUtils.isEmpty(userSessInfo.getCases()))
            {
                if (userSessInfo.getUserDetails() != null)
                {
                    try
                    {
                        // Get the cases for User
                        // userSessInfo.setCases(essSrv.getCases4User(userSessInfo.getUserDetails().getUsAcConEmpl()));

                        // Get ONLY Learning Cases for User
                        userSessInfo.setCases(essSrv.getCases4User(userSessInfo.getUserDetails().getUsAcConEmpl(),
                                EnumCaseTypes.Learning));

                        if (CollectionUtils.isNotEmpty(userSessInfo.getSubmissionIDs()))
                        {
                            // Seek Case IDs for Submissions
                            updateCases4SubmissionIds();
                        }

                    }
                    catch (Exception e)
                    {
                        // Log error
                        log.error(msgSrc.getMessage("ERR_CASES_USER", new Object[]
                        { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), e.getLocalizedMessage() },
                                Locale.ENGLISH));

                        // Raise Exception to be handled at UI via Central Aspect
                        throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASES_USER", new Object[]
                        { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), e.getLocalizedMessage() },
                                Locale.ENGLISH));
                    }
                }
            }

        }

        return this.userSessInfo;
    }

    // @formatter:off -- Submit Case Form
    // : After comsumer Call to Rate Limit is Successful - Caller Resp.
    // : Form Data Saved in session :currentForm4Submission
    // --Validate Case Form - Implicit Call
    // ---- Fail
    // ------- Message Logging Event
    //
    // ------- Message Stack in Session Populated and REturn false
    // ---- Succ
    // ------- Create and Publish Case Submit Event
    // ------- session :currentForm4Submission : update valid flag to be picked up
    // ------- by Event Handler
    // @formatter:on
    @Override
    public boolean SubmitCaseForm(TY_Case_Form caseForm)
    {
        boolean isSubmitted = true;

        // Clear Buffer for Previous Form Submission
        clearPreviousSubmission4mSessionBuffer();

        if (caseForm != null && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()) && vHlpModelSrv != null)
        {
            // Push Form data to Session
            TY_CaseFormAsync caseFormAsync = new TY_CaseFormAsync();

            // Check if Country/Language are Mandatory for chosen Category in Form at time
            // of Submission

            // Make the Case Enum Scan Generic
            // Get Case Type Enum from Case Transaction Type
            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(f -> f.getCaseType().equals(caseForm.getCaseTxnType())).findFirst();
            if (cusItemO.isPresent())
            {

                Map<String, List<TY_KeyValue>> vHlpsMap = vHlpModelSrv
                        .getVHelpUIModelMap4LobCatg(cusItemO.get().getCaseTypeEnum(), caseForm.getCatgDesc());
                // Some Attributes Relevant for Current Category
                if (vHlpsMap.size() > 0)
                { // Country Field Relevant
                    if (CollectionUtils.isNotEmpty(vHlpsMap.get(GC_Constants.gc_LSO_COUNTRY)))
                    {
                        caseForm.setCountryMandatory(true);
                    }
                    else // Remove if Country field is not relevant for Current Category and passed on //
                         // from Form Buffer
                    {
                        caseForm.setCountry(null);
                    }

                    // Language Field Relevant
                    if (CollectionUtils.isNotEmpty(vHlpsMap.get(GC_Constants.gc_LSO_LANGUAGE)))
                    {
                        caseForm.setLangMandatory(true);
                    }
                    else // Remove if Country field is not relevant for Current Category and passed on
                         // from Form Buffer
                    {
                        caseForm.setLanguage(null);
                    }

                }

            }

            caseFormAsync.setCaseForm(caseForm);
            caseFormAsync.setSubmGuid(UUID.randomUUID().toString());
            // Latest time Stamp from Form Submissions
            caseFormAsync.setTimestamp(Timestamp.from(Instant.now()));
            caseFormAsync.setUserId(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId());

            if (!CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))

            {

                if (cusItemO.isPresent() && catalogSrv != null)
                {
                    String[] catTreeSelCatg = catalogSrv.getCatgHierarchyforCatId(caseForm.getCatgDesc(),
                            cusItemO.get().getCaseTypeEnum());
                    caseFormAsync.setCatTreeSelCatg(catTreeSelCatg);
                }

                userSessInfo.setCurrentForm4Submission(caseFormAsync);

                // Validate Case Form : Implicit Call
                if (this.isCaseFormValid())
                {

                    // Attchments to be handled Here in Session service Only once the Form is
                    // Validated Successfully
                    try
                    {
                        if (caseForm.getAttachment() != null)
                        {
                            if (caseForm.getAttachment().getBytes() != null)
                            {
                                // Create Attachment here to prevent Data loss in ASyncronous Thread
                                // Create Attachment
                                TY_Attachment newAttachment = new TY_Attachment(
                                        FilenameUtils.getName(caseForm.getAttachment().getOriginalFilename()),
                                        GC_Constants.gc_Attachment_Category, false);
                                caseFormAsync.setAttR(srvCloudApiSrv.createAttachment(newAttachment));
                                if (caseFormAsync.getAttR() != null)
                                {
                                    if (srvCloudApiSrv.persistAttachment(caseFormAsync.getAttR().getUploadUrl(),
                                            caseForm.getAttachment()))
                                    {
                                        log.info("Attachment with id : " + caseFormAsync.getAttR()
                                                + " Persisted in Document Container.. for Submission ID: "
                                                + caseFormAsync.getSubmGuid());
                                    }
                                }

                            }
                        }

                    }
                    catch (EX_ESMAPI | IOException e)
                    {

                        if (e instanceof IOException)
                        {
                            handleNoFileDataAttachment(caseFormAsync);
                        }
                        else if (e instanceof EX_ESMAPI)
                        {
                            handleAttachmentPersistError(caseFormAsync, e);
                        }

                        isSubmitted = false;
                    }
                    userSessInfo.getCurrentForm4Submission().setValid(true);

                    // SUCC_CASE_SUBM=Case with submission id - {0} of Type - {1} submitted
                    // Successfully for User - {2}!
                    String msg = msgSrc.getMessage("SUCC_CASE_SUBM", new Object[]
                    { caseFormAsync.getSubmGuid(), caseFormAsync.getCaseForm().getCaseTxnType(),
                            caseFormAsync.getUserId() }, Locale.ENGLISH);
                    log.info(msg); // System Log

                    // Logging Framework
                    TY_Message logMsg = new TY_Message(caseFormAsync.getUserId(), caseFormAsync.getTimestamp(),
                            EnumStatus.Success, EnumMessageType.SUCC_CASE_SUBM, caseFormAsync.getSubmGuid(), msg);
                    userSessInfo.getMessagesStack().add(logMsg);
                    // Instantiate and Fire the Event : Syncronous processing
                    EV_LogMessage logMsgEvent = new EV_LogMessage(this, logMsg);
                    applicationEventPublisher.publishEvent(logMsgEvent);

                    // Add to Display Messages : to be shown to User or Successful Submission
                    this.addSessionMessage(msg);

                    // Add Submission Guids to Session Context for REconcillation after Case
                    // Creation Process
                    this.userSessInfo.getSubmissionIDs().add(caseFormAsync.getSubmGuid());

                    isSubmitted = true;

                }
                else // Error Handling :Payload Error
                {
                    // Message Handling Implicitly done via call to Form Validity Check
                    isSubmitted = false;
                }

            }

        }

        return isSubmitted;
    }

    @Override
    public String createAccount() throws EX_ESMAPI
    {
        String accountId = null;
        // Only if no Account or Employee Identified in Current Session
        // No Account Determined
        if (!StringUtils.hasText(userSessInfo.getUserDetails().getUsAcConEmpl().getAccountId()))
        {
            // No Employee determined
            if (!StringUtils.hasText(userSessInfo.getUserDetails().getUsAcConEmpl().getEmployeeId()))
            {
                // Create new Individual Customer Account with User Credentials
                // User Email and UserName Bound
                if (StringUtils.hasText(userSessInfo.getUserDetails().getUsAcConEmpl().getUserEmail())
                        && StringUtils.hasText(userSessInfo.getUserDetails().getUsAcConEmpl().getUserName()))
                {

                    try
                    {
                        accountId = srvCloudApiSrv.createAccount(
                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserEmail(),
                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserName());
                        // Also update in the session for newly created Account
                        if (StringUtils.hasText(accountId))
                        {
                            userSessInfo.getUserDetails().getUsAcConEmpl().setAccountId(accountId);
                            // Session Display Message
                            this.addSessionMessage(msgSrc.getMessage("NEW_AC", new Object[]
                            { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId() }, Locale.ENGLISH));
                            // Add to Log
                            log.info(msgSrc.getMessage("NEW_AC", new Object[]
                            { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId() }, Locale.ENGLISH));

                            TY_Message message = new TY_Message(
                                    userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                    Timestamp.from(Instant.now()), EnumStatus.Success, EnumMessageType.SUCC_ACC_CREATE,
                                    accountId, msgSrc.getMessage("NEW_AC", new Object[]
                                    { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId() }, Locale.ENGLISH));
                            // For Logging Framework
                            userSessInfo.getMessagesStack().add(message);
                            // Instantiate and Fire the Event
                            EV_LogMessage logMsgEvent = new EV_LogMessage(this, message);
                            applicationEventPublisher.publishEvent(logMsgEvent);
                        }
                    }
                    catch (EX_ESMAPI ex) // Any Error During Individual Customer Creation for the User
                    {
                        log.error(msgSrc.getMessage("ERR_API_AC", new Object[]
                        { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId() }, Locale.ENGLISH));

                        TY_Message message = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_ACC_CREATE,
                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                msgSrc.getMessage("ERR_API_AC", new Object[]
                                { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId() }, Locale.ENGLISH));
                        // For Logging Framework
                        userSessInfo.getMessagesStack().add(message);
                        // Instantiate and Fire the Event
                        EV_LogMessage logMsgEvent = new EV_LogMessage(this, message);
                        applicationEventPublisher.publishEvent(logMsgEvent);

                    }

                }

            }
        }

        return accountId;
    }

    @Override
    public Ty_UserAccountContactEmployee getUserDetails4mSession()
    {
        if (this.userSessInfo.getUserDetails() != null)
        {
            return userSessInfo.getUserDetails().getUsAcConEmpl();
        }

        return null;
    }

    @Override
    public void addSessionMessage(String msg)
    {
        if (StringUtils.hasText(msg))
        {
            if (userSessInfo.getMessages() == null)
            {
                userSessInfo.setMessages(new ArrayList<String>());
            }
            userSessInfo.getMessages().add(msg);
        }
    }

    @Override
    public List<String> getSessionMessages()
    {
        return userSessInfo.getMessages();
    }

    @Override
    public boolean isWithinRateLimit()
    {

        boolean withinRateLimit = true;

        // Rate Config Specified
        if (rlConfig != null)
        {
            if (CollectionUtils.isNotEmpty(userSessInfo.getFormSubmissions().getFormSubmissions()))
            {
                // Current # Submissions more than or equals to # configurable - check
                if (userSessInfo.getFormSubmissions().getFormSubmissions().size() >= rlConfig.getNumFormSubms())
                {
                    // Get Current Time Stamp
                    Timestamp currTS = Timestamp.from(Instant.now());
                    // Get Top N :latest Submissions since submissions are always appended
                    // chronologically
                    List<Timestamp> topNSubmList = new ArrayList<Timestamp>();
                    topNSubmList = userSessInfo.getFormSubmissions().getFormSubmissions();
                    Collections.sort(topNSubmList, Collections.reverseOrder());

                    // Compare the Time difference from the latest one
                    long secsElapsedLastSubmit = (currTS.getTime() - topNSubmList.get(0).getTime()) / 1000;
                    // Last Submission elapsed time less than
                    if (secsElapsedLastSubmit < rlConfig.getIntvSecs())
                    {
                        withinRateLimit = false;
                        userSessInfo.setRateLimitBreached(true);
                        log.error(msgSrc.getMessage("ERR_RATE_LIMIT", new Object[]
                        { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(currTS) }, Locale.ENGLISH));

                        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                currTS, EnumStatus.Error, EnumMessageType.ERR_RATELIMIT,
                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                msgSrc.getMessage("ERR_RATE_LIMIT", new Object[]
                                { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                        new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(currTS) }, Locale.ENGLISH));
                        // For Logging Framework
                        userSessInfo.getMessagesStack().add(logMsg);
                        // Instantiate and Fire the Event
                        EV_LogMessage logMsgEvent = new EV_LogMessage(this, logMsg);
                        applicationEventPublisher.publishEvent(logMsgEvent);

                    }
                    else
                    {
                        // Clear the older Submissions Details - Since now the clock is refreshed for
                        // Current Session after Waiting
                        userSessInfo.getFormSubmissions().getFormSubmissions().clear();
                        withinRateLimit = true;
                        userSessInfo.setRateLimitBreached(false);
                        userSessInfo.getFormSubmissions().getFormSubmissions().add(currTS);
                    }

                }
                else // Just add the Form Submission time Stamp to Session
                {
                    userSessInfo.getFormSubmissions().getFormSubmissions().add(Timestamp.from(Instant.now()));
                    userSessInfo.setRateLimitBreached(false);
                    withinRateLimit = true;
                }

            }

            else // Just add the Form Submission time Stamp to Session
            {
                userSessInfo.getFormSubmissions().getFormSubmissions().add(Timestamp.from(Instant.now()));
                userSessInfo.setRateLimitBreached(false);
                withinRateLimit = true;
            }

        }

        return withinRateLimit;
    }

    @Override
    public boolean isCaseFormValid()
    {
        boolean isValid = true;

        // Get the Latest Form Submission from Session and Validate
        if (userSessInfo.getCurrentForm4Submission() != null && catalogSrv != null
                && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
        {
            if (userSessInfo.getCurrentForm4Submission().getCaseForm() != null)
            {
                // Subject and Category are Mandatory fields
                if (StringUtils.hasText(userSessInfo.getCurrentForm4Submission().getCaseForm().getSubject())
                        && StringUtils.hasText(userSessInfo.getCurrentForm4Submission().getCaseForm().getCatgDesc()))
                {

                    // Include Country and Language mandatory check for certain category as
                    // requested by business.
                    if (userSessInfo.getCurrentForm4Submission().getCaseForm().isCountryMandatory())
                    {
                        if (!StringUtils.hasText(userSessInfo.getCurrentForm4Submission().getCaseForm().getCountry()))
                        {
                            // Payload Error as Category level shuld be atleast 2
                            handleMandatoryFieldMissingError(GC_Constants.gc_LSO_COUNTRY_DESC);
                            return false;
                        }
                    }

                    if (userSessInfo.getCurrentForm4Submission().getCaseForm().isLangMandatory())
                    {
                        if (!StringUtils.hasText(userSessInfo.getCurrentForm4Submission().getCaseForm().getLanguage()))
                        {
                            // Payload Error as Category level shuld be atleast 2
                            handleMandatoryFieldMissingError(GC_Constants.gc_LSO_LANGUAGE_DESC);
                            return false;
                        }
                    }

                    Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                            .filter(g -> g.getCaseType()
                                    .equals(userSessInfo.getCurrentForm4Submission().getCaseForm().getCaseTxnType()))
                            .findFirst();

                    if (cusItemO.isPresent())
                    {
                        long catLen = Arrays.stream(catalogSrv.getCatgHierarchyforCatId(
                                userSessInfo.getCurrentForm4Submission().getCaseForm().getCatgDesc(),
                                cusItemO.get().getCaseTypeEnum())).filter(Objects::nonNull).count();
                        // Check that Category is not a level 1 - Base Category
                        if (catLen <= 1)
                        {
                            // Extract Category Description
                            TY_CatalogTree catgTree = catalogSrv.getCaseCatgTree4LoB(cusItemO.get().getCaseTypeEnum());
                            if (catgTree != null)
                            {
                                if (CollectionUtils.isNotEmpty(catgTree.getCategories()))
                                {

                                    // Remove blank Categories from Catalog Tree Used for UI Presentation
                                    catgTree.getCategories().removeIf(x -> x.getId() == null);
                                    Optional<TY_CatalogItem> currCatgDetailsO = catgTree
                                            .getCategories().stream().filter(f -> f.getId().equals(userSessInfo
                                                    .getCurrentForm4Submission().getCaseForm().getCatgDesc()))
                                            .findFirst();
                                    if (currCatgDetailsO.isPresent())
                                    {
                                        // Payload Error as Category level shuld be atleast 2
                                        String msg = msgSrc.getMessage("ERR_CATG_LVL", new Object[]
                                        { currCatgDetailsO.get().getName() }, Locale.ENGLISH);
                                        log.error(msg); // System Log

                                        // Logging Framework
                                        TY_Message logMsg = new TY_Message(
                                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                                                Timestamp.from(Instant.now()), EnumStatus.Error,
                                                EnumMessageType.ERR_PAYLOAD,
                                                userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), msg);
                                        userSessInfo.getMessagesStack().add(logMsg);

                                        // Instantiate and Fire the Event : Syncronous processing
                                        EV_LogMessage logMsgEvent = new EV_LogMessage(this, logMsg);
                                        applicationEventPublisher.publishEvent(logMsgEvent);

                                        this.addFormErrors(msg);// For Form Display

                                    }
                                    // Refurbish Blank Category at Top for New Form - Session maintained
                                    catgTree.getCategories().add(0, new TY_CatalogItem());
                                }
                            }

                            // Add to Display Messages : to be shown to User or Successful Submission
                            isValid = false;

                        }

                        // Also check for Allowed Attachment Type(s) as provided by the business
                        if (rlConfig != null
                                && userSessInfo.getCurrentForm4Submission().getCaseForm().getAttachment() != null)
                        {
                            if (CollectionUtils.isEmpty(userSessInfo.getAllowedAttachmentTypes()))
                            {
                                // Populate Attachment Types allowed in Session
                                userSessInfo.setAllowedAttachmentTypes(
                                        Arrays.asList(rlConfig.getAllowedAttachments().split("\\|")));

                            }
                            if (StringUtils
                                    .hasText(userSessInfo.getCurrentForm4Submission().getCaseForm().getAttachment()
                                            .getOriginalFilename())
                                    && CollectionUtils.isNotEmpty(userSessInfo.getAllowedAttachmentTypes()))
                            {
                                // Get the Extension Type for Attachment
                                String filename = userSessInfo.getCurrentForm4Submission().getCaseForm().getAttachment()
                                        .getOriginalFilename();
                                String[] fNameSplits = filename.split("\\.");

                                if (fNameSplits != null)
                                {
                                    String extensionAttachment = null;
                                    if (fNameSplits.length >= 1)
                                    {
                                        extensionAttachment = fNameSplits[fNameSplits.length - 1];
                                    }
                                    if (StringUtils.hasText(extensionAttachment))
                                    {
                                        String extnType = extensionAttachment;
                                        Optional<String> extnfoundO = userSessInfo.getAllowedAttachmentTypes().stream()
                                                .filter(a -> a.equalsIgnoreCase(extnType)).findFirst();
                                        if (!extnfoundO.isPresent())
                                        {
                                            // Invalid Attachment TYpe Error
                                            handleInvalidAttachment(filename, extnType);
                                            isValid = false;
                                        }
                                    }
                                }

                            }
                        }
                    }

                }
                else
                {

                    // Payload error

                    String msg = msgSrc.getMessage("ERR_CASE_PAYLOAD", new Object[]
                    { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                            new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Timestamp.from(Instant.now())) },
                            Locale.ENGLISH);
                    log.error(msg);

                    TY_Message message = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                            Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_PAYLOAD,
                            userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), msg);

                    // For Logging Framework
                    userSessInfo.getMessagesStack().add(message);
                    // Instantiate and Fire the Event
                    EV_LogMessage logMsgEvent = new EV_LogMessage(this, message);
                    applicationEventPublisher.publishEvent(logMsgEvent);

                    this.addFormErrors(msg);// For Form Display

                    isValid = false;

                }

            }
        }

        return isValid;

    }

    @Override
    public void addMessagetoStack(TY_Message msg)
    {

        userSessInfo.getMessagesStack().add(msg);
    }

    @Override
    public List<TY_Message> getMessageStack()
    {
        return userSessInfo.getMessagesStack();
    }

    @Override
    public boolean getCurrentRateLimitBreachedValue()
    {
        boolean rateLimitBreachedVal = false;
        if (userSessInfo != null)
        {
            rateLimitBreachedVal = userSessInfo.isRateLimitBreached();
        }

        return rateLimitBreachedVal;
    }

    // #Test
    @Override
    public void loadUser4Test()
    {

        if (userSessInfo == null)
        {
            userSessInfo = new TY_UserSessionInfo();
        }
        if (userSessInfo.getUserDetails() == null)
        {

            TY_UserDetails userDetails = new TY_UserDetails();
            userDetails.setAuthenticated(true);
            // userDetails.setRoles(userInfo.getRoles().stream().collect(Collectors.toList()));
            Ty_UserAccountContactEmployee usAccConEmpl = new Ty_UserAccountContactEmployee("I057386", "Sunny Bhardwaj",
                    "sunny.bhardwaj@sap.com", "11eda929-5152-18be-afdb-81d9ac010a00",
                    "11eda929-71b5-43ce-afdb-81d9ac010a00", "11ed17c5-47d5-c4de-afdb-818bd8010a00", false, false);

            userDetails.setUsAcConEmpl(usAccConEmpl);
            userSessInfo.setUserDetails(userDetails); // Set in Session

        }

        if (userSessInfo.getUserDetails().getUsAcConEmpl() != null)
        {
            try
            {
                // Get the cases for User
                // Clear from Buffer
                if (CollectionUtils.isNotEmpty(this.getCases4User4mSession()))
                {
                    userSessInfo.getCases().clear();
                }

                // Fetch Afresh and Reset
                userSessInfo.setCases(
                        essSrv.getCases4User(userSessInfo.getUserDetails().getUsAcConEmpl(), EnumCaseTypes.Learning));
                if (CollectionUtils.isNotEmpty(userSessInfo.getSubmissionIDs()))
                {
                    // Seek Case IDs for Submissions
                    updateCases4SubmissionIds();
                }

            }
            catch (Exception e)
            {
                // Log error
                log.error(msgSrc.getMessage("ERR_CASES_USER", new Object[]
                { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), e.getLocalizedMessage() },
                        Locale.ENGLISH));

                // Raise Exception to be handled at UI via Central Aspect
                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASES_USER", new Object[]
                { userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), e.getLocalizedMessage() },
                        Locale.ENGLISH));
            }

        }

    }

    // #Test
    @Override
    public TY_UserSessionInfo getSessionInfo4Test()
    {
        return this.userSessInfo;
    }

    @Override
    public TY_CaseFormAsync getCurrentForm4Submission()
    {
        TY_CaseFormAsync currCaseForm = null;
        if (userSessInfo != null)
        {
            currCaseForm = userSessInfo.getCurrentForm4Submission();
        }

        return currCaseForm;
    }

    @Override
    public void clearFormErrors()
    {
        this.userSessInfo.getFormErrorMsgs().clear();
    }

    @Override
    public List<String> getFormErrors()
    {
        return this.userSessInfo.getFormErrorMsgs();
    }

    @Override
    public void addFormErrors(String errorMsg)
    {
        if (StringUtils.hasText(errorMsg))
        {
            if (userSessInfo.getFormErrorMsgs() != null)
            {
                userSessInfo.getFormErrorMsgs().add(errorMsg);
            }
            else
            {
                userSessInfo.setFormErrorMsgs(new ArrayList<String>());
                userSessInfo.getFormErrorMsgs().add(errorMsg);
            }
        }
    }

    @Override
    public void clearPreviousSubmission4mSessionBuffer()
    {
        // clear Previous Run Form Error Messages
        if (CollectionUtils.isNotEmpty(userSessInfo.getFormErrorMsgs()))
        {
            this.clearFormErrors();
        }

        // Clear previous run attachment error(s) from Messages Stack
        if (CollectionUtils.isNotEmpty(userSessInfo.getMessagesStack()))
        {
            Optional<TY_Message> attErrO = userSessInfo.getMessagesStack().stream()
                    .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
            if (attErrO.isPresent())
            {
                userSessInfo.getMessagesStack().remove(attErrO.get());

            }
        }
    }

    @Override
    public List<TY_CaseESS> getCases4User4mSession()
    {

        if (CollectionUtils.isNotEmpty(userSessInfo.getCases()))
        {
            log.info("# Cases returned for User From Session : " + userSessInfo.getCases().size());
            return userSessInfo.getCases();
        }

        return null;
    }

    @Override
    public void updateCases4SubmissionIds() throws EX_ESMAPI
    {
        if (hanaLogSrv != null && CollectionUtils.isNotEmpty(userSessInfo.getSubmissionIDs()))
        {
            // Get the Logs for the Object IDS - Sumbission GUIDS
            List<Esmappmsglog> logs = hanaLogSrv.getLogsByObjectIDs(userSessInfo.getSubmissionIDs());
            if (CollectionUtils.isNotEmpty(logs))
            {
                log.info("# Of Log entries for Submission(s) in Current Session - " + logs.size());
                // Get Logs Excluding Successful Submission
                List<Esmappmsglog> logsExclSubm = logs.stream()
                        .filter(l -> !(l.getMsgtype().equalsIgnoreCase(EnumMessageType.SUCC_CASE_SUBM.toString())))
                        .collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(logsExclSubm))
                {
                    for (Esmappmsglog esmappmsglog : logsExclSubm)
                    {
                        log.info("# Of Log Entries after Filtering Case Submissions  - " + logsExclSubm.size());
                        // further Check due to HANA String Comparison Issue - Not picked up in Filter

                        // Append Messages to Session

                        this.addSessionMessage(esmappmsglog.getMessage());
                        // # Performance - REmove the Submission Guids that are reconcilled for
                        // respective Case IDs
                        userSessInfo.getSubmissionIDs().remove(esmappmsglog.getObjectid());

                    }
                }

            }

        }
    }

    @Override
    public TY_CaseEdit_Form getCaseDetails4Edit(String caseID) throws EX_ESMAPI
    {
        TY_CaseEdit_Form caseEditForm = null;
        if (StringUtils.hasText(caseID))
        {
            // Check if REquested Case belongs to the User
            if (CollectionUtils.isNotEmpty(getCases4User4mSession()))
            {
                Optional<TY_CaseESS> caseESSO = getCases4User4mSession().stream()
                        .filter(c -> c.getGuid().equals(caseID)).findFirst();
                if (caseESSO.isPresent())
                {
                    TY_CaseESS caseESS = caseESSO.get();

                    // If The Channel is SELF_SERVICE, then only seek Notes and Use Other Case
                    // details from Session Var
                    if (caseESS.getOrigin().equals(GC_Constants.gc_SelfServiceChannel))
                    {
                        try
                        {
                            TY_CaseDetails caseDetails = srvCloudApiSrv.getCaseDetails4Case(caseESS.getGuid());
                            // --- Filter by External Note Type(s) only
                            if (caseDetails != null)
                            {
                                caseEditForm = new TY_CaseEdit_Form();
                                caseDetails.setCaseType(caseESS.getCaseType());
                                caseDetails.setCaseId(caseESS.getId());
                                caseDetails.setStatus(caseESS.getStatusDesc());
                                caseDetails.setDescription(caseESS.getSubject());
                                if (CollectionUtils.isNotEmpty(caseDetails.getNotes()))
                                {
                                    // Get External Note Type(s) for Current Case Type
                                    if (CollectionUtils.isNotEmpty(catgCusSrv.getCustomizations()))
                                    {
                                        // Get Customization for Current Case Type
                                        Optional<TY_CatgCusItem> cusIO = catgCusSrv.getCustomizations().stream()
                                                .filter(c -> c.getCaseType().equals(caseDetails.getCaseType()))
                                                .findFirst();
                                        if (cusIO.isPresent())
                                        {
                                            // Check if Note Type configured for External Note(s)
                                            if (StringUtils.hasText(cusIO.get().getAppNoteTypes()))
                                            {
                                                List<String> appNoteTypes = Arrays
                                                        .asList(cusIO.get().getAppNoteTypes().split("\\|"));
                                                if (CollectionUtils.isNotEmpty(appNoteTypes))
                                                {
                                                    // Filter by External Note Type(s) only
                                                    List<TY_NotesDetails> notesExternal = caseDetails.getNotes()
                                                            .stream()
                                                            .filter(n -> appNoteTypes.contains(n.getNoteType()))
                                                            .collect(Collectors.toList());

                                                    if (CollectionUtils.isNotEmpty(notesExternal))
                                                    {

                                                        Collections.sort(notesExternal, Comparator
                                                                .comparing(TY_NotesDetails::getTimestamp).reversed());
                                                        caseDetails.setNotes(notesExternal);
                                                    }

                                                }
                                            }
                                        }
                                    }

                                }

                                caseEditForm.setCaseDetails(caseDetails);

                            }
                        }
                        catch (Exception e)
                        {
                            handleErrorCaseFetch(caseID, e);
                        }

                    }

                    // Else Only Use Case Details from Sess Var and prepare the Case Edit Form Model
                }
                else // Unauthorized Access - Case Is not in User's List
                {
                    handleUnauthorizedCaseAccess(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), caseID);
                }
            }

        }

        return caseEditForm;
    }

    private void handleErrorCaseFetch(String caseID, Exception e)
    {
        String msg;
        msg = msgSrc.getMessage("EXC_CASE_GET", new Object[]
        { caseID, e.getLocalizedMessage() }, Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_CASE_DET_FETCH, caseID, msg);
        this.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) caseID, logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleUnauthorizedCaseAccess(String userId, String caseID)
    {
        String msg;
        msg = msgSrc.getMessage("ERR_UNAUTH_CASE_ACCESS", new Object[]
        { userId, caseID }, Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(userId, Timestamp.from(Instant.now()), EnumStatus.Error,
                EnumMessageType.ERR_UNAUTH_CASE_ACCESS, caseID, msg);
        this.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) caseID, logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleMandatoryFieldMissingError(String fldName)
    {
        String msg = msgSrc.getMessage("ERR_MAND_FLDS", new Object[]
        { fldName }, Locale.ENGLISH);
        log.error(msg); // System Log

        // Logging Framework
        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_PAYLOAD,
                userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(), msg);
        userSessInfo.getMessagesStack().add(logMsg);

        // Instantiate and Fire the Event : Syncronous processing
        EV_LogMessage logMsgEvent = new EV_LogMessage(this, logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        this.addFormErrors(msg);// For Form Display
    }

    private void handleInvalidAttachment(String filename, String extnType)
    {
        String msg;
        msg = msgSrc.getMessage("ERR_INVALID_ATT_TYPE", new Object[]
        { extnType, filename }, Locale.ENGLISH);

        log.error(msg);
        this.addFormErrors(msg);
        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_ATTACHMENT,
                userSessInfo.getCurrentForm4Submission().getSubmGuid(), msg);
        this.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) userSessInfo.getCurrentForm4Submission().getSubmGuid(),
                logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

    }

    private void handleAttachmentPersistError(TY_CaseFormAsync caseFormAsync, Exception e)
    {
        String msg;
        msg = msgSrc.getMessage("ERROR_DOCS_PERSIST", new Object[]
        { caseFormAsync.getAttR().getUploadUrl(), caseFormAsync.getCaseForm().getAttachment().getOriginalFilename(),
                e.getLocalizedMessage() }, Locale.ENGLISH);

        log.error(msg);

        this.addFormErrors(msg);// For Form Display

        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_ATTACHMENT,
                caseFormAsync.getSubmGuid(), msg);
        this.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) caseFormAsync.getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);
        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

    private void handleNoFileDataAttachment(TY_CaseFormAsync caseFormAsync)
    {
        String msg;
        msg = msgSrc.getMessage("FILE_NO_DATA", new Object[]
        { caseFormAsync.getCaseForm().getAttachment().getOriginalFilename() }, Locale.ENGLISH);

        log.error(msg);
        TY_Message logMsg = new TY_Message(userSessInfo.getUserDetails().getUsAcConEmpl().getUserId(),
                Timestamp.from(Instant.now()), EnumStatus.Error, EnumMessageType.ERR_ATTACHMENT,
                caseFormAsync.getSubmGuid(), msg);
        this.addMessagetoStack(logMsg);

        // Instantiate and Fire the Event
        EV_LogMessage logMsgEvent = new EV_LogMessage((Object) caseFormAsync.getSubmGuid(), logMsg);
        applicationEventPublisher.publishEvent(logMsgEvent);

        // Should be handled Centrally via Aspect
        throw new EX_ESMAPI(msg);
    }

}
