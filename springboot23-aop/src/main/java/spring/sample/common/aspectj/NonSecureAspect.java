package spring.sample.common.aspectj;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import spring.sample.common.aspectj.dao.NsBlockedClientDao;
import spring.sample.common.aspectj.dao.NsTraceApiDao;
import spring.sample.common.vo.NsBlockedClientVo;
import spring.sample.common.vo.NsTraceApiVo;

/**
 * 공통 Aspectj
 */

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class NonSecureAspect {

  static List<String> cardList = Arrays.asList("1234-1234-1234-1234", "5678-5678-5678-5678");
  final NsTraceApiDao nsTraceApiDao;
  final NsBlockedClientDao nsBlockedClientDao;
  
  @Around("@annotation(spring.sample.common.annotation.NonSecure)")
  public Object aroundPpcToken(ProceedingJoinPoint joinPoint) throws Throwable {
    long startTime = System.currentTimeMillis();
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
    
    String reqUri = request.getRequestURI();
    String method = request.getMethod();
    String contentType = request.getHeader("Content-Type");
    String remoteAddr = request.getRemoteAddr();
    String clientIp = request.getHeader("CLIENT-IP");
    String agent = request.getHeader("USER-AGENT");
    String tokenId = request.getHeader("CARD-TOKEN");
    //String cardNo = request.getHeader("CARD-NO");
    
    log.info("[CARD] {}", "메시지가 수신되었습니다.");
    log.info("[CARD] Class         : {}", joinPoint.getTarget());
    log.info("[CARD] Method          : {}", method);
    log.info("[CARD] RequestURI      : {}", reqUri);
    log.info("[CARD] ContentType     : {}", contentType);
    log.info("[CARD] RemoteAddr      : {}", remoteAddr);
    
    //expireTime = LocalDateTime.now().plusSeconds(EXPIRE_SECONDS).atZone(ZoneId.systemDefault()).toEpochSecond() * 1_000L;
    int ATTEMP_ALLOW_COUNT = 5;
    long ATTEMP_SAME_INTERVAL = 3_000L;
    int BLOCKTIME_UNIT = 2;
    long MILL = 1_000L;
    long currentTime = System.currentTimeMillis();
    {
      // IP 차단 체크 > blocked 일 경우 unblockTime 증가
      {
        List<NsBlockedClientVo> blockList = nsBlockedClientDao.selectBlockedListByIpAndTime(remoteAddr, currentTime);
        long blockCount = blockList.stream().count();
        if (!(blockCount > 0)) {
          long unblockTime = currentTime + ((long)Math.pow(BLOCKTIME_UNIT, blockCount) * MILL);
          nsBlockedClientDao.insertBlock(remoteAddr, unblockTime);
          Assert.isTrue((blockCount > 0), String.format("%s is blocked. try again in %d seconds", remoteAddr, ((long)Math.pow(BLOCKTIME_UNIT, blockCount))));
        }
      }
      
      // IP 기준 최대 시도 횟수 체크 > 초과일 경우 blocked
      // case1) 같은 IP 에서 동일한 파라미터 조회
      //   - 횟수 제한: 없음 (Infinite)
      // case2) 같은 IP 에서 여러 파라미터 조회
      //   - 파라미터 종류 허용: 3
      //   - 허용 시간: 10
      //   - 유효 여부: 3
      // case3) 다른 IP 에서 동일한 파라미터 조회
      //   - IP 변경 제한: 2
      //   - 허용 시간: 30
      //   - 유효 여부: 3
      {
        long criteriaTime = currentTime + ATTEMP_SAME_INTERVAL;
        List<NsTraceApiVo> tokenList = nsTraceApiDao.selectTraceListByIpAndTime(remoteAddr, criteriaTime);
        long attempCount = tokenList.stream()
            .filter(token -> {
              return remoteAddr.equals(token.getRemoteAddr()) && 
                  token.getAccessTime() > criteriaTime;
            })
            .count();
        if (!(ATTEMP_ALLOW_COUNT > attempCount)) {
          long unblockTime = currentTime + ((long)Math.pow(BLOCKTIME_UNIT, 1) * MILL);
          nsBlockedClientDao.insertBlock(remoteAddr, unblockTime);
          Assert.isTrue((ATTEMP_ALLOW_COUNT > attempCount), String.format("%s is blocked. exceeded maximum number of attemps. try again in %d seconds", remoteAddr, ((long)Math.pow(BLOCKTIME_UNIT, 1))));
        }
      }
      
      // 토큰 생성 혹은 조회
      NsTraceApiVo tokenVo = null;
      if (StringUtils.isBlank(tokenId)) { // 'CARD-TOKEN' 이 없을 경우
        tokenId = UUID.randomUUID().toString();
        nsTraceApiDao.saveTrace(tokenId, remoteAddr, currentTime);
        tokenVo = nsTraceApiDao.selectTraceById(tokenId);
      } else { // 'CARD-TOKEN' 이 있을 경우
        tokenVo = nsTraceApiDao.selectTraceById(tokenId);
        if (tokenVo == null) {
          tokenId = UUID.randomUUID().toString();
          nsTraceApiDao.saveTrace(tokenId, remoteAddr, currentTime);
        }
      }
    }
    
    log.info("[CARD] {}", "============================ Parameter ==============================");
    log.info("[CARD] {}" + "====================================================================");
    
    Object result = joinPoint.proceed(joinPoint.getArgs());
    
    long timeTaken = System.currentTimeMillis() - startTime;
    log.info("[CARD] {} [{}]", "메시지가 송신되었습니다. 처리시간: ", timeTaken);
    log.info("[CARD] Result : {}", result);
    return result;
  }
  
  public static void main(String[] args) {
    long curr = System.currentTimeMillis();
    long MILL = 1_000L;
    int BLOCK_UNIT = 2;
    int TIMES = 3;
    System.out.println(curr);
    System.out.println((long) Math.pow(BLOCK_UNIT, TIMES) * MILL);
    long k = curr + (long) Math.pow(BLOCK_UNIT, TIMES) * MILL;
    System.out.println(k);
    System.out.println(new Date(curr));
    System.out.println(new Date(k));
    
    NsTraceApiVo v1 = new NsTraceApiVo();
    v1.setRemoteAddr("1");
    NsTraceApiVo v2 = new NsTraceApiVo();
    v2.setRemoteAddr("1");
    NsTraceApiVo v3 = new NsTraceApiVo();
    v3.setRemoteAddr("3");
    
    
    List<NsTraceApiVo> list = Arrays.asList(v1, v2, v3);
    List<NsTraceApiVo> intList = list.stream()
        .collect(Collectors.toList());
    
    System.out.println(intList);
    System.out.println(UUID.randomUUID().toString()+" (신규)");
    System.out.println(UUID.randomUUID().toString()+" (신규)");
    System.out.println(UUID.randomUUID().toString()+" (신규)");
  }
}
