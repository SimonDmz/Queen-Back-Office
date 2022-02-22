package fr.insee.queen.api.repository.impl;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insee.queen.api.controller.StateDataController;
import fr.insee.queen.api.dto.statedata.StateDataDto;
import fr.insee.queen.api.dto.surveyunit.SurveyUnitResponseDto;
import fr.insee.queen.api.repository.SimpleApiRepository;

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

@Service
public class SimplePostgreSQLRepository implements SimpleApiRepository {

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(SimplePostgreSQLRepository.class);

    @Override
    public void updateSurveyUnitData(String id, JsonNode data) {
        updateJsonValueOfSurveyUnit("data",id,data);
    }

    @Override
    public void updateSurveyUnitComment(String id, JsonNode comment)  {
        updateJsonValueOfSurveyUnit("comment",id,comment);
    }

    @Override
    public void updateSurveyUnitPersonalization(String id, JsonNode personalization) {
        updateJsonValueOfSurveyUnit("personalization",id, personalization);
    }

    public String getCampaignIdFromSuId(String id){
        String qStringGetCampaignID= "SELECT campaign_id FROM survey_unit WHERE id=?";
        return jdbcTemplate.queryForObject(
                qStringGetCampaignID, new Object[] { id }, String.class);
    }

    @Override
    public void updateSurveyUnitStateDate(String id, JsonNode stateData){

        String qStringGetSU = "SELECT count(*) FROM state_data WHERE survey_unit_id=?";
        Long date = stateData.get("date").longValue();
        String state = stateData.get("state").textValue();
        String currentPage = stateData.get("currentPage").textValue();

        int nbStateData = jdbcTemplate.queryForObject(
                qStringGetSU, new Object[] { id }, Integer.class);

        if(nbStateData>0) {
            String qString = "UPDATE state_data SET current_page=?, date=?, state=? WHERE survey_unit_id=?";
            jdbcTemplate.update(qString, currentPage, date, state, id);
        }else{
            LOGGER.info("INSERT state_data for reporting unit with id {}", id);
            String qString = "INSERT INTO state_data (id, current_page, date, state, survey_unit_id) VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(qString, UUID.randomUUID(), currentPage, date, state, id);
        }

    }



    @Override
    public void createSurveyUnit(String campaignId, SurveyUnitResponseDto surveyUnitResponseDto) {
        String su ="INSERT INTO survey_unit (id, campaign_id, questionnaire_model_id)\n" +
                "VALUES (?,?,?)\n" +
                "ON CONFLICT (id) DO UPDATE SET campaign_id=?, questionnaire_model_id=?";
        LOGGER.info("Request for SU creation : {}", su);
        jdbcTemplate.update(su,
                surveyUnitResponseDto.getId(),
                campaignId, surveyUnitResponseDto.getQuestionnaireId(),
                campaignId, surveyUnitResponseDto.getQuestionnaireId());

        insertJsonValueOfSurveyUnit("data",surveyUnitResponseDto.getId(),surveyUnitResponseDto.getData());
        insertJsonValueOfSurveyUnit("comment",surveyUnitResponseDto.getId(),surveyUnitResponseDto.getComment());
        insertJsonValueOfSurveyUnit("personalization",surveyUnitResponseDto.getId(),surveyUnitResponseDto.getPersonalization());
        insertSurveyUnitStateDate(surveyUnitResponseDto.getId(),surveyUnitResponseDto.getStateData());
    }

    private void insertSurveyUnitStateDate(String surveyUnitId, StateDataDto stateData){
        if (stateData == null || stateData.getDate() == null || stateData.getState()==null )
            return;
        Long date = stateData.getDate();
        String state = stateData.getState().name();
        String currentPage = stateData.getCurrentPage();
        String qString = "INSERT INTO state_data (id,current_page,date,state,survey_unit_id) VALUES (?,?,?,?,?)";
        LOGGER.info("Request for SU state-data insertion : {}", qString);
        jdbcTemplate.update(qString,UUID.randomUUID(),currentPage,date,state,surveyUnitId);
    }

    private void insertJsonValueOfSurveyUnit(String table, String surveyUnitId, JsonNode jsonValue){
        String qString = String.format("INSERT INTO %s (id, value, survey_unit_id) VALUES (?,?,?)",table);
        LOGGER.info("Request for SU Json value insertion : {}", qString);
        PGobject json = new PGobject();
        json.setType("json");
        try {
            json.setValue(jsonValue.toString());
        } catch (SQLException throwables) {
            LOGGER.error("Error when inserting in {} - {}",table,throwables.getMessage());
            throwables.printStackTrace();
        }
        jdbcTemplate.update(qString,UUID.randomUUID(),json,surveyUnitId);
    }


    private void updateJsonValueOfSurveyUnit(String table, String surveyUnitId, JsonNode jsonValue) {
        String qString = String.format("UPDATE %s SET value=? WHERE survey_unit_id=?",table);
        PGobject q = new PGobject();
        q.setType("json");
        try {
            q.setValue(jsonValue.toString());
        } catch (SQLException throwables) {
            LOGGER.error("Error when inserting in {} - {}",table,throwables.getMessage());
            throwables.printStackTrace();
        }
        jdbcTemplate.update(qString, q, surveyUnitId);
    }
}
