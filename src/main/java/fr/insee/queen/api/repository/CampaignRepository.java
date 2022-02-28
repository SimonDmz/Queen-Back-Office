package fr.insee.queen.api.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import fr.insee.queen.api.domain.Campaign;
import fr.insee.queen.api.dto.campaign.CampaignDto;
import fr.insee.queen.api.dto.campaign.CampaignResponseDto;

/**
 * CampaignRepository is the repository using to access to Campaign table in DB
 * 
 * @author Claudel Benjamin
 * 
 */
@Transactional
@Repository
public interface CampaignRepository extends JpaRepository<Campaign, String> {
	/**
	 * This method retrieve all Campaign in DB
	 * 
	 * @return List of all {@link CampaignDto}
	 */
	// @Query(value = "select c.id , c.questionnaireModels.id from Campaign join c")
	@EntityGraph(attributePaths = "questionnaireModels.id")
	// @Query(nativeQuery = true, value = "select qm.id as questionnaireIds,
	// qm.campaign_id as id from questionnaire_model qm where qm.campaign_id in
	// (select id from campaign c )")
	// @Query(nativeQuery = true, value = "SELECT new
	// fr.insee.queen.api.dto.campaign.CampaignResponseDto(qm.campaign_id,) FROM
	// QuestionnaireModel qm qm.id as questionnaireIds, qm.campaign_id as id from
	// questionnaire_model qm where qm.campaign_id in (select id from campaign c )")
	@Query("SELECT DISTINCT c FROM Campaign join fetch c.questionnaireModels qm )")
	List<CampaignResponseDto> findDtoBy();

	List<Campaign> findAll();

	Optional<Campaign> findById(String id);
}
