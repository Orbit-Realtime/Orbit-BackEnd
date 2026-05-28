//package com.chat;
//
//import com.chat.entity.Member;
//import com.chat.repository.MemberRepository;
//import com.chat.service.utils.PasswordEncoder;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//@Component
//@Profile("dev")
//@RequiredArgsConstructor
//public class InitDB {
//
//    private final InitService initService;
//
//    @PostConstruct
//    public void init() {
//        initService.dbInit();
//    }
//
//    @Component
//    @Transactional
//    @RequiredArgsConstructor
//    static class InitService {
//
//        private final MemberRepository memberRepository;
//        private final PasswordEncoder passwordEncoder;
//
//        public void dbInit() {
//            Member a = createMember("a");
//            Member b = createMember("b");
//            Member c = createMember("c");
//            Member d = createMember("d");
//        }
//
//        private Member createMember(String name) {
//            return memberRepository.save(Member.of(name, passwordEncoder.encode(name),  name));
//        }
//    }
//}
