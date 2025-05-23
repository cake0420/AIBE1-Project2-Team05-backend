package com.team05.linkup.domain.mentoring.application;

import com.team05.linkup.common.dto.UserPrincipal;
import com.team05.linkup.common.exception.UserNotfoundException;
import com.team05.linkup.common.util.ApiUtils;
import com.team05.linkup.domain.enums.Interest;
import com.team05.linkup.domain.mentoring.dto.AiMatchingRequestDTO;
import com.team05.linkup.domain.mentoring.dto.AiMatchingResponseDTO;
import com.team05.linkup.domain.mentoring.dto.ProfileTagInterestDTO;
import com.team05.linkup.domain.mentoring.util.NgrokApiUrl;
import com.team05.linkup.domain.mentoring.util.RecommendationLogic;
import com.team05.linkup.domain.user.infrastructure.CustomerUserRepositoryImpl;
import com.team05.linkup.domain.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class AiMatchingListServiceImpl implements AiMatchingService {
    private static final Logger logger = LogManager.getLogger();
    private final UserRepository userRepository;
    private final CustomerUserRepositoryImpl customerUserRepositoryImpl;
    private final ApiUtils apiUtils;
    private final RecommendationLogic recommendationLogic;

    private final NgrokApiUrl apiUrl;

    @Override
    public AiMatchingResponseDTO matchMentor(UserPrincipal userPrincipal) throws TimeoutException {
        try {
            String provider = userPrincipal.provider();
            String providerId = userPrincipal.providerId();
            ProfileTagInterestDTO result = userRepository.findProfileTagAndInterestByProviderAndProviderId(provider,providerId);
//
            logger.debug(result);

            String myProfileTag = result.profileTag();
            Interest myInterest = result.interest();

            logger.debug("myProfileTag: {}, myInterest: {}", myProfileTag, myInterest);
            List<AiMatchingRequestDTO.OtherProfile> resultList = customerUserRepositoryImpl.findOtherProfileTagsByProviderId(provider,
                                                                                        providerId,
                                                                                        myInterest);
            logger.debug("resultList: {}", resultList);

            AiMatchingRequestDTO requestDTO = new AiMatchingRequestDTO(myProfileTag, resultList);
            logger.debug("api url: {}", apiUrl);
            String url = "%s/word-similarity".formatted(apiUrl.getApiUrl());

            Optional<AiMatchingResponseDTO> responseOpt = apiUtils.getApiResponse(url, "POST", requestDTO, AiMatchingResponseDTO.class);

            AiMatchingResponseDTO response = responseOpt.orElseThrow(() ->
                    new UserNotfoundException("mentor is not found"));
            List<AiMatchingResponseDTO.Result> sampled = recommendationLogic.weightedRandomSample(response.results(), 4);

            Collections.shuffle(sampled);
            logger.debug("sampled: {}", sampled);
            return new AiMatchingResponseDTO(myProfileTag, sampled);

        } catch (UserNotfoundException e) {
            logger.error("user is not found: {}", e.getMessage());
            throw new UserNotfoundException("user is not found: " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            logger.error("IndexOutOfBoundsException: {}", e.getMessage());
            throw new IndexOutOfBoundsException("IndexOutOfBoundsException: " + e.getMessage());
        }
        catch (Exception e) {
            logger.error("Error in matchMentor: {}", e.getMessage());
            throw new RuntimeException("Error in matchMentor: " + e.getMessage(), e);
        }

    }
}
