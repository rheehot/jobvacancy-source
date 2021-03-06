package com.jobvacancy.web.rest;

import com.jobvacancy.Application;
import com.jobvacancy.domain.JobOffer;
import com.jobvacancy.domain.User;
import com.jobvacancy.repository.JobOfferRepository;
import com.jobvacancy.repository.UserRepository;
import com.jobvacancy.service.MailService;
import com.jobvacancy.web.rest.dto.JobApplicationDTO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.verification.VerificationModeFactory;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Optional;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@WebAppConfiguration
@IntegrationTest
public class ApplicationResourceTest {

    private static final String APPLICANT_FULLNAME = "THE APPLICANT";
    private static final String APPLICANT_EMAIL = "APPLICANT@TEST.COM";
    private static final String APPLICANT_VALID_URL = "http://www.micv.com/micv";
    private static final String APPLICANT_INVALID_URL = "www.%#&micv.com/micv";
    private MockMvc restMockMvc;

    private static final long OFFER_ID = 1;
    private static final String OFFER_TITLE = "SAMPLE_TEXT";

    @Mock
    private MailService mailService;

    @Mock
    private JobOfferRepository jobOfferRepository;

    @Inject
    private UserRepository userRepository;

    @Mock
    private UserRepository mockUserRepository;

    @Inject
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    private JobOffer offer;

    @PostConstruct
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Optional<User> user = userRepository.findOneByLogin("user");
        when(mockUserRepository.findOneByLogin(Mockito.any())).thenReturn(user);
        offer = createJobOffer(user, 2);
        when(jobOfferRepository.findOne(OFFER_ID)).thenReturn(offer);

        JobApplicationResource jobApplicationResource = new JobApplicationResource();
        ReflectionTestUtils.setField(jobApplicationResource, "jobOfferRepository", jobOfferRepository);
        ReflectionTestUtils.setField(jobApplicationResource, "mailService", mailService);
        ReflectionTestUtils.setField(jobApplicationResource, "userRepository", mockUserRepository);

        this.restMockMvc = MockMvcBuilders.standaloneSetup(jobApplicationResource)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    @Test
    @Transactional
    public void createJobApplication() throws Exception {
        JobApplicationDTO dto = createJobApplicationDTO(OFFER_ID, APPLICANT_FULLNAME, APPLICANT_EMAIL,
            APPLICANT_VALID_URL);

        Optional<User> anonymousUser = userRepository.findOneByLogin("anonymousUser");
        Optional<User> user = userRepository.findOneByLogin("user");

        when(mockUserRepository.findOneByLogin(Mockito.any())).thenReturn(anonymousUser);
        JobOffer offer2 = createJobOffer(user, 2);
        when(jobOfferRepository.findOne(OFFER_ID)).thenReturn(offer2);

        doNothing().when(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer2);

        postToAPI(dto);

        Mockito.verify(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer);
    }

    @Test
    @Transactional
    public void sendEmailForMaxCapacityWhenApplyMaxCapacity() throws Exception {
        JobApplicationDTO dto = createJobApplicationDTO(OFFER_ID, APPLICANT_FULLNAME, APPLICANT_EMAIL,
            APPLICANT_VALID_URL);

        Optional<User> anonymousUser = userRepository.findOneByLogin("anonymousUser");
        Optional<User> user = userRepository.findOneByLogin("user");

        when(mockUserRepository.findOneByLogin(Mockito.any())).thenReturn(anonymousUser);
        JobOffer offer2 = createJobOffer(user, 2);
        when(jobOfferRepository.findOne(OFFER_ID)).thenReturn(offer2);

        doNothing().when(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer2);

        postToAPI(dto);
        postToAPI(dto);

        Mockito.verify(mailService).sendEmailForMaxCapacity(offer);
    }

    @Test
    @Transactional
    public void createJobApplicationThrowErrorWhenIsPublishedByYourself() throws Exception {
        JobApplicationDTO dto = createJobApplicationDTO(OFFER_ID, APPLICANT_FULLNAME, APPLICANT_EMAIL,
            APPLICANT_VALID_URL);

        doNothing().when(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer);

        try {
            restMockMvc.perform(post("/api/Application").contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(dto)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Cannot apply to offer published by yourself"));
        }
    }

    @Test
    @Transactional
    public void createJobApplicationThrowError() throws Exception {
        JobApplicationDTO dto = createJobApplicationDTO(OFFER_ID, APPLICANT_FULLNAME, APPLICANT_EMAIL,
            APPLICANT_INVALID_URL);

        doNothing().when(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer);

        try {
            restMockMvc.perform(post("/api/Application").contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(dto)));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("Invalid url"));
        }
    }

    @Test
    @Transactional
    public void notSendEmailForMaxCapacityWhenThereIsCapacity() throws Exception {
        JobApplicationDTO dto = createJobApplicationDTO(OFFER_ID, APPLICANT_FULLNAME, APPLICANT_EMAIL,
            APPLICANT_VALID_URL);

        Optional<User> anonymousUser = userRepository.findOneByLogin("anonymousUser");
        Optional<User> user = userRepository.findOneByLogin("user");

        when(mockUserRepository.findOneByLogin(Mockito.any())).thenReturn(anonymousUser);
        JobOffer offer2 = createJobOffer(user, 2);
        when(jobOfferRepository.findOne(OFFER_ID)).thenReturn(offer2);

        doNothing().when(mailService).sendApplication(APPLICANT_EMAIL, dto.getUrl(), offer2);

        postToAPI(dto);

        Mockito.verify(mailService, VerificationModeFactory.times(0)).sendEmailForMaxCapacity(offer2);
    }

    private JobApplicationDTO createJobApplicationDTO(Long offerId, String fullname, String email, String url) {
        JobApplicationDTO dto = new JobApplicationDTO();
        dto.setEmail(email);
        dto.setFullname(fullname);
        dto.setOfferId(offerId);
        dto.setUrl(url);
        return dto;
    }

    private void postToAPI(JobApplicationDTO dto) throws Exception {
        restMockMvc.perform(post("/api/Application").contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))).andExpect(status().isAccepted());
    }

    private JobOffer createJobOffer(Optional<User> user, int maxCapacity) {
        JobOffer offer = new JobOffer();
        offer.setTitle(OFFER_TITLE);
        offer.setId(OFFER_ID);
        offer.setOwner(user.get());
        offer.setCapacity(maxCapacity);
        return offer;
    }

}
