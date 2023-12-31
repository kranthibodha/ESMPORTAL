package com.sap.cap.esmapi.ui.controllers;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sap.cap.esmapi.catg.pojos.TY_CatgCus;
import com.sap.cap.esmapi.catg.pojos.TY_CatgCusItem;
import com.sap.cap.esmapi.catg.pojos.TY_CatgTemplates;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatalogSrv;
import com.sap.cap.esmapi.catg.srv.intf.IF_CatgSrv;
import com.sap.cap.esmapi.events.event.EV_CaseFormSubmit;
import com.sap.cap.esmapi.exceptions.EX_ESMAPI;
import com.sap.cap.esmapi.status.pojos.TY_StatusCfgItem;
import com.sap.cap.esmapi.ui.pojos.TY_CaseEdit_Form;
import com.sap.cap.esmapi.ui.pojos.TY_Case_Form;
import com.sap.cap.esmapi.utilities.enums.EnumCaseTypes;
import com.sap.cap.esmapi.utilities.enums.EnumMessageType;
import com.sap.cap.esmapi.utilities.pojos.TY_Message;
import com.sap.cap.esmapi.utilities.pojos.TY_UserESS;
import com.sap.cap.esmapi.utilities.srv.intf.IF_UserSessionSrv;
import com.sap.cap.esmapi.utilities.srvCloudApi.srv.intf.IF_SrvCloudAPI;
import com.sap.cap.esmapi.vhelps.srv.intf.IF_VHelpLOBUIModelSrv;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/poclocal")
@Slf4j
public class POCLocalController
{
    @Autowired
    private IF_UserSessionSrv userSessSrv;

    @Autowired
    private TY_CatgCus catgCusSrv;

    @Autowired
    private IF_CatgSrv catgTreeSrv;

    @Autowired
    private IF_CatalogSrv catalogTreeSrv;

    @Autowired
    private MessageSource msgSrc;

    @Autowired
    private IF_VHelpLOBUIModelSrv vhlpUISrv;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    // #TEST - Begin
    @Autowired
    private IF_SrvCloudAPI apiSrv;

    // #TEST - End

    private final String caseListVWRedirect = "redirect:/poclocal/";
    private final String caseFormErrorRedirect = "redirect:/poclocal/errForm/";
    private final String caseFormView = "caseFormPOCLocal";

    @GetMapping("/")
    public String showCasesList(Model model)
    {

        TY_UserESS userDetails = new TY_UserESS();

        // Mocking the authentication

        if (userSessSrv != null)
        {
            userSessSrv.loadUser4Test();
            // check User and Account Bound
            if (userSessSrv.getUserDetails4mSession() != null)
            {
                if (StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                        || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                {
                    if (!CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
                    {

                        Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                                .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString()))
                                .findFirst();
                        if (cusItemO.isPresent() && catgTreeSrv != null)
                        {
                            userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                            userDetails.setCases(userSessSrv.getSessionInfo4Test().getCases());
                            model.addAttribute("userInfo", userDetails);
                            model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());
                            // Rate Limit Simulation
                            model.addAttribute("rateLimitBreached", userSessSrv.getCurrentRateLimitBreachedValue());

                            // Even if No Cases - spl. for Newly Create Acc - to enable REfresh button
                            model.addAttribute("sessMsgs", userSessSrv.getSessionMessages());

                        }

                        else
                        {

                            throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                            { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
                        }
                    }

                }
            }

        }

        return "essListViewPOCLocal";

    }

    @GetMapping("/createCase/")
    public String showCaseAsyncForm(Model model)
    {
        String viewCaseForm = caseFormView;

        if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
        {

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Before case form Inititation we must check the Rate Limit for the Current
                // User Session --current Form Submission added for Rate Limit Evaulation
                if (userSessSrv.isWithinRateLimit())
                {
                    // Populate User Details
                    TY_UserESS userDetails = new TY_UserESS();
                    userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                    model.addAttribute("userInfo", userDetails);

                    // Populate Case Form Details
                    TY_Case_Form caseForm = new TY_Case_Form();
                    if (userSessSrv.getUserDetails4mSession().isEmployee())
                    {
                        caseForm.setAccId(userSessSrv.getUserDetails4mSession().getEmployeeId()); // hidden
                    }
                    else
                    {
                        caseForm.setAccId(userSessSrv.getUserDetails4mSession().getAccountId()); // hidden
                    }

                    caseForm.setCaseTxnType(cusItemO.get().getCaseType()); // hidden
                    model.addAttribute("caseForm", caseForm);

                    model.addAttribute("formErrors", null);

                    // also Upload the Catg. Tree as per Case Type
                    model.addAttribute("catgsList",
                            catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                }
                else
                {
                    // Not Within Rate Limit - REdirect to List View
                    viewCaseForm = caseListVWRedirect;

                }

            }
            else
            {

                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
            }

        }

        return viewCaseForm;
    }

    @PostMapping(value = "/saveCase", params = "action=saveCase")
    public String saveCase(@ModelAttribute("caseForm") TY_Case_Form caseForm, Model model)
    {

        String viewName = caseListVWRedirect;
        if (caseForm != null && userSessSrv != null)
        {
            if (userSessSrv.getUserDetails4mSession().isEmployee())
            {
                caseForm.setEmployee(true);
            }

            log.info("Processing of Case Form - UI layer :Begins....");

            // Any Validation Error(s) on the Form or Submission not possible
            if (!userSessSrv.SubmitCaseForm(caseForm))
            {
                // Redirect to Error Processing of Form
                viewName = caseFormErrorRedirect;
            }
            else
            {
                // Fire Case Submission Event - To be processed Asyncronously
                EV_CaseFormSubmit eventCaseSubmit = new EV_CaseFormSubmit(this,
                        userSessSrv.getCurrentForm4Submission());
                applicationEventPublisher.publishEvent(eventCaseSubmit);
            }

            log.info("Processing of Case Form - UI layer :Ends....");
        }

        return viewName;

    }

    @GetMapping("/errForm/")
    public String showErrorCaseForm(Model model)
    {

        if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations())
                && userSessSrv.getCurrentForm4Submission() != null)
        {

            Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                    .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString())).findFirst();
            if (cusItemO.isPresent() && catgTreeSrv != null)
            {

                model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                // Populate User Details
                TY_UserESS userDetails = new TY_UserESS();
                userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                model.addAttribute("userInfo", userDetails);

                // Populate Case Form Details
                TY_Case_Form caseForm = new TY_Case_Form();
                if (userSessSrv.getUserDetails4mSession().isEmployee())
                {
                    caseForm.setAccId(userSessSrv.getUserDetails4mSession().getEmployeeId()); // hidden
                }
                else
                {
                    caseForm.setAccId(userSessSrv.getUserDetails4mSession().getAccountId()); // hidden
                }

                caseForm.setCaseTxnType(cusItemO.get().getCaseType()); // hidden
                caseForm.setCatgDesc(userSessSrv.getCurrentForm4Submission().getCaseForm().getCatgDesc()); // Curr Catg
                caseForm.setDescription(userSessSrv.getCurrentForm4Submission().getCaseForm().getDescription()); // Curr
                                                                                                                 // Notes
                caseForm.setSubject(userSessSrv.getCurrentForm4Submission().getCaseForm().getSubject()); // Curr Subject

                if (StringUtils.hasText(userSessSrv.getCurrentForm4Submission().getCaseForm().getCountry()))
                {
                    caseForm.setCountry(userSessSrv.getCurrentForm4Submission().getCaseForm().getCountry());
                }

                if (StringUtils.hasText(userSessSrv.getCurrentForm4Submission().getCaseForm().getLanguage()))
                {
                    caseForm.setLanguage(userSessSrv.getCurrentForm4Submission().getCaseForm().getLanguage());
                }

                model.addAttribute("formErrors", userSessSrv.getFormErrors());

                // Not Feasible to have a Validation Error in Form and Attachment Persisted -
                // But just to handle theoratically in case there is an Error in Attachment
                // Persistence only- Remove the attachment otherwise let it persist
                if (CollectionUtils.isNotEmpty(userSessSrv.getMessageStack()))
                {
                    Optional<TY_Message> attErrO = userSessSrv.getMessageStack().stream()
                            .filter(e -> e.getMsgType().equals(EnumMessageType.ERR_ATTACHMENT)).findFirst();
                    if (!attErrO.isPresent())
                    {
                        // Attachment able to presist do not remove it from Current Payload
                        caseForm.setAttachment(userSessSrv.getCurrentForm4Submission().getCaseForm().getAttachment());

                    }
                }

                if (vhlpUISrv != null)
                {
                    model.addAllAttributes(
                            vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                }

                model.addAttribute("caseForm", caseForm);

                // also Upload the Catg. Tree as per Case Type
                model.addAttribute("catgsList",
                        catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

            }
            else
            {

                throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
            }

        }

        return caseFormView;
    }

    @PostMapping(value = "/saveCase", params = "action=catgChange")
    public String refreshCaseForm4Catg(@ModelAttribute("caseForm") TY_Case_Form caseForm, Model model)
    {

        String viewCaseForm = caseFormView;
        if (caseForm != null && userSessSrv != null)
        {

            // Normal Scenario - Catg. chosen Not relevant for Notes Template and/or
            // additional fields

            if ((StringUtils.hasText(userSessSrv.getUserDetails4mSession().getAccountId())
                    || StringUtils.hasText(userSessSrv.getUserDetails4mSession().getEmployeeId()))
                    && !CollectionUtils.isEmpty(catgCusSrv.getCustomizations()))
            {

                Optional<TY_CatgCusItem> cusItemO = catgCusSrv.getCustomizations().stream()
                        .filter(g -> g.getCaseTypeEnum().toString().equals(EnumCaseTypes.Learning.toString()))
                        .findFirst();
                if (cusItemO.isPresent() && catgTreeSrv != null)
                {

                    model.addAttribute("caseTypeStr", EnumCaseTypes.Learning.toString());

                    // Populate User Details
                    TY_UserESS userDetails = new TY_UserESS();
                    userDetails.setUserDetails(userSessSrv.getUserDetails4mSession());
                    model.addAttribute("userInfo", userDetails);

                    // Account Or Employee already set on the Form
                    model.addAttribute("formErrors", userSessSrv.getFormErrors());

                    // also Upload the Catg. Tree as per Case Type
                    model.addAttribute("catgsList",
                            catalogTreeSrv.getCaseCatgTree4LoB(EnumCaseTypes.Learning).getCategories());

                    // Scan Current Catg for Templ. Load and or Additional Fields

                    // Scan for Template Load
                    TY_CatgTemplates catgTemplate = catalogTreeSrv.getTemplates4Catg(caseForm.getCatgDesc(),
                            EnumCaseTypes.Learning);
                    if (catgTemplate != null)
                    {

                        // Set Questionnaire for Category
                        caseForm.setTemplate(catgTemplate.getQuestionnaire());

                    }

                    if (vhlpUISrv != null)
                    {
                        model.addAllAttributes(
                                vhlpUISrv.getVHelpUIModelMap4LobCatg(EnumCaseTypes.Learning, caseForm.getCatgDesc()));
                    }

                    // Case Form Model Set at last
                    model.addAttribute("caseForm", caseForm);
                }
                else
                {

                    throw new EX_ESMAPI(msgSrc.getMessage("ERR_CASE_TYPE_NOCFG", new Object[]
                    { EnumCaseTypes.Learning.toString() }, Locale.ENGLISH));
                }

            }

        }

        return viewCaseForm;

    }

    // #TEST
    @GetMapping("/caseDetails/{caseID}")
    public String getCaseDetails(@PathVariable String caseID, Model model)
    {
        if (StringUtils.hasText(caseID) && apiSrv != null)
        {
            if (userSessSrv != null)
            {
                userSessSrv.loadUser4Test();

                try
                {

                    TY_CaseEdit_Form caseEditForm = userSessSrv.getCaseDetails4Edit(caseID);
                    if (caseEditForm != null)
                    {
                        if (CollectionUtils.isNotEmpty(caseEditForm.getCaseDetails().getNotes()))
                        {
                            log.info("# External Notes Bound for Case ID - "
                                    + caseEditForm.getCaseDetails().getNotes().size());
                        }
                    }
                }
                catch (Exception e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        return "success";
    }

    // #TEST
    @GetMapping("/schDetails/{schID}")
    public String getStatusSchemaDetails(@PathVariable String schID)
    {
        if (StringUtils.hasText(schID) && apiSrv != null)
        {

            try
            {

                List<TY_StatusCfgItem> cfgs = apiSrv.getStatusCfg4StatusSchema(schID);

                if (CollectionUtils.isNotEmpty(cfgs))
                {
                    for (TY_StatusCfgItem cfg : cfgs)
                    {
                        log.info(cfg.toString());
                    }
                }

            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return "success";
    }

}
