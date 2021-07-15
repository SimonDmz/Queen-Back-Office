package fr.insee.queen.api.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import fr.insee.queen.api.constants.Constants;
import fr.insee.queen.api.domain.Campaign;
import fr.insee.queen.api.domain.SurveyUnit;
import fr.insee.queen.api.dto.statedata.StateDataDto;
import fr.insee.queen.api.dto.surveyunit.SurveyUnitDto;
import fr.insee.queen.api.dto.surveyunit.SurveyUnitResponseDto;
import fr.insee.queen.api.exception.BadRequestException;
import fr.insee.queen.api.service.CampaignService;
import fr.insee.queen.api.service.SurveyUnitService;
import fr.insee.queen.api.service.UtilsService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
* SurveyUnitController is the Controller using to manage {@link SurveyUnit} entity
* 
* @author Claudel Benjamin
* 
*/
@RestController
@RequestMapping(path = "/api")
public class SurveyUnitController {
	private static final Logger LOGGER = LoggerFactory.getLogger(SurveyUnitController.class);

	@Value("${fr.insee.queen.pilotage.integration.override:#{null}}")
	private String integrationOverride;
	/**
	* The survey unit repository using to access to table 'survey_unit' in DB 
	*/
	@Autowired
	private SurveyUnitService surveyUnitService;
	
	
	@Autowired
	private UtilsService utilsService;
	
	@Autowired
	private CampaignService campaignService;
	
	
	/**
	* This method is used to get a survey-unit by id
	*/
	@ApiOperation(value = "Get survey-unit")
	@GetMapping(path = "/survey-unit/{id}")
	public ResponseEntity<SurveyUnitResponseDto> getSurveyUnitById(HttpServletRequest request, @PathVariable(value = "id") String id) {
		Optional<SurveyUnit> suOpt = surveyUnitService.findById(id);
		if(!suOpt.isPresent()) {
			LOGGER.error("GET survey-units with id {} resulting in 404", id);
			return ResponseEntity.notFound().build();
		}
		String userId = utilsService.getUserId(request);
		if(!userId.equals(Constants.GUEST) && (!utilsService.checkHabilitation(request, id, Constants.INTERVIEWER) 
				&& !utilsService.checkHabilitation(request, id, Constants.REVIEWER))) {
			LOGGER.error("GET survey-unit for reporting unit with id {} resulting in 403", id);
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		SurveyUnit su = suOpt.get();
		SurveyUnitResponseDto resp = new SurveyUnitResponseDto(
				null, 
				su.getQuestionnaireModelId(), 
				su.getPersonalization()==null ? null :su.getPersonalization().getValue(), 
				su.getData()==null ? null : su.getData().getValue(), 
				su.getComment()==null ? null : su.getComment().getValue(),
				su.getStateData()==null ? null : new StateDataDto(su.getStateData()));
		LOGGER.info("GET survey-unit resulting in 200");
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}

	/**
	 * This method is used to update a survey-unit by id
	 */
	@ApiOperation(value = "Put survey-unit")
	@PutMapping(path = "/survey-unit/{id}")
	public ResponseEntity<Object> putSurveyUnitById(@RequestBody JsonNode surveyUnit, HttpServletRequest request, @PathVariable(value = "id") String id) {
		String userId = utilsService.getUserId(request);
		if(!userId.equals(Constants.GUEST) && !utilsService.checkHabilitation(request, id, Constants.INTERVIEWER)) {
			LOGGER.error("PUT survey-unit for reporting unit with id {} resulting in 403", id);
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		surveyUnitService.updateSurveyUnitWithoutHibernate(id, surveyUnit);
		LOGGER.info("PUT survey-units resulting in 200");
		return ResponseEntity.ok().build();
	}
	
	/**
	* This method is using to get all survey units associated to a specific campaign 
	* 
	* @param id the id of campaign
	* @return List of {@link SurveyUnitDto}
	 * @throws ParseException 
	 * @throws IOException 
	*/
	
	
	@ApiOperation(value = "Get list of survey units by camapign Id ")
	@GetMapping(path = "/campaign/{id}/survey-units")
	public ResponseEntity<Object> getListSurveyUnitByCampaign(HttpServletRequest request, @PathVariable(value = "id") String id) {
		Optional<Campaign> campaignOptional = campaignService.findById(id);
		if (!campaignOptional.isPresent()) {
			LOGGER.error("GET survey-units for campaign with id {} resulting in 404", id);
			return ResponseEntity.notFound().build();
		}
		
    String userId = utilsService.getUserId(request);
    if(!userId.equals(Constants.GUEST) && 
      !(integrationOverride != null && integrationOverride.equals("true"))) {
			try {
				Collection<SurveyUnitResponseDto> lstSuByCampaign = surveyUnitService.getSurveyUnitsByCampaign(id, request);
				if(lstSuByCampaign.isEmpty()) {
					return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
				}
				return new ResponseEntity<>(lstSuByCampaign, HttpStatus.OK);
			}catch (BadRequestException e) {
				LOGGER.error(e.getMessage());
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
			}
		} else {
			LOGGER.info("GET survey-units for campaign with id {} resulting in 200", id);
			List<SurveyUnit> results = surveyUnitService.findByCampaignId(id);
			if(results.isEmpty()) {
				return ResponseEntity.notFound().build();
			}else {
				List<SurveyUnitResponseDto> resp = results.stream()
					.map(su -> new SurveyUnitResponseDto(su.getId(), su.getQuestionnaireModelId(),null, null, null, null))
					.collect(Collectors.toList());
				return new ResponseEntity<>(resp, HttpStatus.OK);
			}
		}
	}
	
	@ApiOperation(value = "Get deposit proof for a SU ")
	@GetMapping(value = "/survey-unit/{id}/deposit-proof")
	public ResponseEntity<StreamingResponseBody> getDepositProof(@PathVariable(value = "id") String id, HttpServletRequest request) {
		Optional<SurveyUnit> suOpt = surveyUnitService.findById(id);
		if(!suOpt.isPresent()) {
			LOGGER.error("GET deposit-proof with id {} resulting in 404", id);
			return ResponseEntity.notFound().build();
		}
		String userId = utilsService.getUserId(request);
		if(!userId.equals(Constants.GUEST) && (!utilsService.checkHabilitation(request, id, Constants.INTERVIEWER) 
				&& !utilsService.checkHabilitation(request, id, Constants.REVIEWER))) {
			LOGGER.error("GET deposit-proof for reporting unit with id {} resulting in 403", id);
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		SurveyUnit su = suOpt.get();
		if (su.getStateData()!=null) {
			try {
				File pdfFile = surveyUnitService.generateDepositProof(su, userId);
				String fileName = String.format("%s-%s.fo",su.getCampaign().getId(),userId);
				StreamingResponseBody stream = out -> out.write(Files.readAllBytes(pdfFile.toPath()));

				return  ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\""+fileName+"\"")
						//.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
						.body(stream);
			} catch (Exception e) {
				return new ResponseEntity("ERROR_EXPORT " + Arrays.toString(e.getStackTrace()) , HttpStatus.INTERNAL_SERVER_ERROR);
			}
			
	    }
        LOGGER.error("GET deposit-proof with id {} resulting in 404", id);
        return ResponseEntity.notFound().build();
	
	}
	
	/**
	* This method is using to create a survey-unit
	* 
	* @param id the id of campaign
	* @return List of {@link String} containing nomenclature ids
	*/
	@ApiOperation(value = "Post survey-unit")
	@PostMapping(path = "/campaign/{id}/survey-unit")
	public ResponseEntity<Object> postSurveyUnit(@RequestBody SurveyUnitResponseDto su, @PathVariable(value = "id") String id){
		if(!utilsService.isDevProfile() && !utilsService.isTestProfile()) {
			return ResponseEntity.notFound().build();
		}
		HttpStatus status = surveyUnitService.postSurveyUnitWithoutHibernate(id, su);
		return new ResponseEntity<>(status);
	}
	
	/**
	* This method is using to create a survey-unit
	* 
	* @param id the id of campaign
	* @return List of {@link String} containing nomenclature ids
	*/
	@ApiOperation(value = "Delete survey-unit")
	@DeleteMapping(path = "/survey-unit/{id}")
	public ResponseEntity<Object> deleteSurveyUnit(HttpServletRequest request, @PathVariable(value = "id") String id){
		Optional<SurveyUnit> surveyUnitOptional = surveyUnitService.findById(id);
		if (!surveyUnitOptional.isPresent()) {
			LOGGER.error("DELETE survey-unit with id {} resulting in 404 because it does not exists", id);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		surveyUnitService.deleteById(surveyUnitOptional.get());
		LOGGER.info("DELETE survey-unit with id {} resulting in 200", id);
		return ResponseEntity.ok().build();
	}
	
}
