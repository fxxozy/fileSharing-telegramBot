package ru.vsu.cs.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vsu.cs.dto.MailParams;
import ru.vsu.cs.entities.AppUser;
import ru.vsu.cs.repositories.AppUserRepository;
import ru.vsu.cs.services.AppUserService;
import ru.vsu.cs.utils.CryptoTool;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import static ru.vsu.cs.enums.UserState.BASIC_STATE;
import static ru.vsu.cs.enums.UserState.WAIT_FOR_EMAIL_STATE;

@Log4j
@RequiredArgsConstructor
@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserRepository appUserRepository;

    private final CryptoTool cryptoTool;

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.queues.registration-mail}")
    private String registrationMailQueue;

    @Override
    public String registerUser(AppUser appUser) {
        if (appUser.isActive()) {
            return "Вы уже зарегистрированы!";
        } else if (appUser.getEmail() != null) {
            return "Вам на почту уже было отправлено письмо. "
                    + "Перейдите по ссылке в письме для подтверждения регистрации!";
        }
        appUser.setState(WAIT_FOR_EMAIL_STATE);
        appUserRepository.save(appUser);
        return "Пожалуйста, введите Ваш email:";
    }

    @Override
    public String setEmail(AppUser appUser, String email) {
        try {
            var emailAddress = new InternetAddress(email);
            emailAddress.validate();
        } catch (AddressException e) {
            return "Пожалуйста, введите корректный email. Для отмена команды нажмите /cancel";
        }
        var appUserOpt = appUserRepository.findByEmail(email);
        if (appUserOpt.isEmpty()) {
            appUser.setEmail(email);
            appUser.setState(BASIC_STATE);
            appUser = appUserRepository.save(appUser);

            var cryptoUserId = cryptoTool.hashOf(appUser.getId());
            sendRegistrationMail(cryptoUserId, email);

            return "Вам на почту было отправлено письмо."
                    + "Перейдите по ссылке в письме для подтверждения регистрации.";
        } else {
            return "Этот email уже используется. Введите корректный email."
                    + "Для отмены команды ведите /cancel";
        }

    }

    private void sendRegistrationMail(String cryptoUserId, String email) {
        var mailParams = MailParams.builder()
                .id(cryptoUserId)
                .emailTo(email)
                .build();
        rabbitTemplate.convertAndSend(registrationMailQueue, mailParams);
    }
}
