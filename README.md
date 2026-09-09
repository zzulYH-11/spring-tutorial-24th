# 스프링의 세 기둥, 그들의 관계

IoC/DI, AOP, PSA는 스프링을 지탱하는 세 기둥이라고 불리는데, 사실 서로 독립적인 기술은 아니고 아래와 같이 레이어처럼 쌓여 있다.

```
IoC/DI -> AOP -> PSA
```


IoC/DI가 “컨테이너가 객체를 관리한다”라는 기반을 깔아주고,

그 위에서 AOP가 객체를 프록시로 바꾸면서 여러 공통 기능을 추가해주고,

그러한 AOP의 기능들을 추상화하여 묶은 결과물이 PSA인 것.

지금은 당연히 이해가 안 갈 것이다. 하나씩 상세히 알아본 뒤에 다시 읽어보며 이해하기로 하고, 우선 이 세 기술이 동작하는 데 핵심적인 역할을 하는 Parsing과 Reflection을 간단히 이해해보자.

# 파싱과 리플렉션

파싱과 리플렉션 모두 코드(혹은 문자열)에서 필요한 정보를 뽑아내는 기술이다. 

- 파싱

파싱은 날것의 데이터를 정해진 문법을 기준으로 분해해서, 프로그램이 이해하고 다룰 수 있는 형태로 바꾸는 작업을 말한다.

서버에 들어오는 HTTP 요청을 생각해보자. 컴퓨터 입장에선 단순한 데이터들의 나열일 뿐이다. 하지만 우리는 HTTP 요청이 어떤 규칙으로 작성되어있는지 알기 때문에, Tomcat이 이를 바탕으로 텍스트를 순서대로 읽어가며 의미 있는 데이터(메서드, 경로, 헤더 등)를 뽑아낼 수 있다.

스프링에서는 대표적으로 빈 등록 과정에서 바이트 코드를 훑어가며 클래스의 정보를 읽을 때 파싱이 사용된다.

(+) 이때 클래스를 실제로 로딩하지 않고 바이트코드만 가볍게 훑기 때문에,
수많은 클래스를 스캔해야 하는 컴포넌트 스캔 과정에서도 성능 부담이 적다.

- 리플렉션

스프링은 실행 중에 클래스 정보를 알아내고 그에 맞춰 동적으로 객체를 다뤄야 한다. 다만 스프링은 개발자가 어떤 클래스를 만들지 컴파일 시점에 알 수 없다. 그래서 스프링은 프로그램이 실행되는 도중(런타임)에 자기 자신의 코드 구조(클래스, 메서드, 필드 등)을 스스로 들여다보고 조작할 수 있는 기능이 필요하고, 이 기술이 바로 리플렉션이다. 리플렉션은 java.lang.reflect 패키지를 통해 지원된다.

예를 들어 빈을 생성할 때 생성자를 호출하거나, @Autowired 필드에 값을 주입하는 것도 전부 리플렉션으로 이루어지는데, 이는 나중에 살펴볼 것이다

- 파싱과 리플렉션의 관계

스프링의 많은 동작은 이 둘의 조합으로 이루어진다. 예를 들어 컴포넌트 스캔은, 파싱(바이트코드 읽기)으로 "어떤 클래스에 어떤 어노테이션이 있는지" 먼저 알아내고, 그 다음 리플렉션으로 실제 그 클래스의 생성자를 호출해 객체를 만든다. 즉 파싱이 "무엇이 있는지 알아내는" 역할이라면, 리플렉션은
"그것을 실제로 다루는" 역할을 한다.

이제 IoC/DI부터 시작해서 스프링의 핵심 기술들을 차례로 살펴보자.

# IoC/DI

IoC(Inversion of Control)란, 객체의 생성 및 관리 책임이 개발자가 아닌 프레임워크에 있는 디자인 패턴, 혹은 그러한 상태를 말한다.

스프링은 이러한 IoC를 DI(Dependency Injection)라는 것을 통해 구현한다.

- 왜 등장한걸까?

아래 코드를 살펴보자.

```java
public class MemberService {
	//private final MemberRepository memberRepositoy = new JpaMemberRepository();
	private final MemberRepository memberRepository = new MemoryMemberRepository();
}
```

개발자가 직접 `new()` 를 통해 객체를 생성하고 있다.

이러면 객체 지향 설계 원칙 SOLID의 관점에서 몇가지 결함을 발견할 수 있다.

1. OCP 위반!

OCP는 확장에는 열려있고, 수정에는 닫혀있어야 한다는 것이다. 그러나 만약에 우리가 MemberRepository의 새 구현체를 구현하여 추가하려면, 직접 기존 코드를 수정해야 한다. 즉, 수정에 닫혀있지 못한 코드인 것이다(주석 참고)

2. DIP 위반!

SOLID는 구현체가 아닌 인터페이스에 의존하는 것이 좋은 설계라고 한다.(필수는 아니지만)

위 코드는 new()를 통해 구현체에 의존하고 있으므로, 바람직하지 못하다.

앞서 살펴봤듯, 객체 생성 책임이 개발자에게 있을 때 여러 설계 문제가 발생한다. 스프링에서는 이를 DI로 해결한다고 했는데, 이를 구체적으로 살펴보자.

- DI

스프링은 스프링 컨테이너가 직접 객체를 생성하고 필요한 객체(의존성)을 주입해주는 방식으로 동작한다. 이게 바로 DI인 것. 스프링 컨테이너가 어떤 과정을 통해 빈을 관리하는지는 이후에 다룰 것이고, 우선 의존성 주입의 세 가지 방법을 살펴보자.

1. 생성자 주입

```java
@Service
//@RequiredArgsConstructor 를 쓰면 생성자와 @AutoWired 생략 가능
public class MemberService {

    private final MemberRepository memberRepository;

    @Autowired  // 생성자가 하나뿐이면 생략 가능
    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}
```

이렇게 작성하면, 스프링 컨테이너가 MemberService가 생성되는 시점에 MemberRepository를 같이 주입해준다. 개발자는 new()를 통해 직접 MemberRepository를 생성할 필요가 없는 것이다.

생성자 주입을 사용하면,

- MemberService는 어떤 Repository 구현체가 주입될 지 신경쓰지 않아도 됨 → 결합이 느슨해짐
- 객체 생성 시점에 스프링 컨테이너가 final 필드에 초기화되므로, 의존 관계의 불변성을 보장하고, 초기화 누락 문제를 컴파일 과정에서 즉시 잡아낼 수 있음.
- Mock 객체를 사용하기 용이함

2. 수정자(세터) 주입

```java
@Service
public class MemberService {

    private MemberRepository memberRepository;  

    @Autowired
    public void setMemberRepository(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}
```

변경 가능성이 있는 의존 관계에 활용한다. 객체를 먼저 생성해두고, @Autowired 어노테이션이 붙은 세터를 호출하여 의존성을 나중에 주입한다. 

3. 필드 주입

```java
@Service
public class MemberService {

    @Autowired
    private MemberRepository memberRepository;  
}
```

코드가 제일 간결하다. 그러나, final 키워드를 쓸 수 없으므로, 불변성을 보장하지 못한다.(수정자 주입도 마찬가지)

# AOP

메서드의 실행 시간을 측정하고 싶다. 과연 프로젝트에 있는 모든 클래스의 앞, 뒷부분에 시간 측정 및 로깅 로직을 추가할 수 있을까? 프로젝트의 규모가 클 수록 이는 불가능에 가까워질 것이다.

시간 측정과 같이 하나의 기능이 여러 클래스에 흩어져서 반복되는 것들이 있다. 로깅, 트랜잭션 관리, 보안 체크 같은 것들이 그러하다. 이런 것을 Cross-Cutting Concern이라고 하고, 이런 Cross-Cutting Concern들을 하나의 공통 로직으로 처리하도록 모듈화하여 삽입하는 방식이 바로 AOP이다.

스프링은 프록시 패턴을 이용하여 AOP를 지원한다. 이미 DI를 통한 IoC 패턴으로 객체 관리 책임이 프레임워크로 넘어갔으므로, 스프링은 우리가 객체를 호출하면, 스프링은 부가 기능이 포함된 프록시를 만들어 리턴하는 식으로 AOP를 지원하는 것.

# PSA (Portable Service Abstraction)

`@Transactional` 은 우리가 어떤 DB 접근 기술을 쓰든 똑같이 동작한다. 

만약 이런 추상화가 없다면, 기술을 바꿀 때마다 트랜잭션 관련 코드를 전부 다시 짜야 한다. 스프링은 이 문제를, PlatformTransactionManager라는 공통 인터페이스를 정의해두고 기술마다 그 인터페이스의 구현체(DataSourceTransactionManager, JpaTransactionManager 등)를 갖다 붙이는 방식으로 해결한다. 이게 가능한 이유는 앞서 다룬 AOP 덕분이다 

@Transactional이 붙은 메서드는 프록시로 감싸져서, 실행 전후에 트랜잭션 시작/커밋 로직이 자동으로 끼워 넣어진다. 즉 PSA는 AOP라는 메커니즘을 활용해서, 기술 구현체가 무엇이든 일관된 어노테이션 하나로 다룰 수 있게 만든 설계 원칙이다.

# 정리

IoC/DI가 “컨테이너가 객체를 관리한다”라는 기반을 깔아주고,

그 위에서 AOP가 객체를 프록시로 바꾸면서 여러 공통 기능을 추가해주고,

그러한 AOP의 기능들을 추상화하여 묶은 결과물이 PSA인 것.


---
---

# 스프링 컨테이너와 빈

## 1. 스프링 컨테이너란?

스프링 컨테이너는 스프링에서 객체의 생명주기와 의존성을 대신 관리해주는 구현체다. IoC 컨테이너라고도 부르는데, IoC 컨테이너 쪽이 좀 더 일반적이고 넓은 개념이라고 생각하면 된다. `ApplicationContext`가 스프링 컨테이너의 핵심 인터페이스다.

스프링 컨테이너가 담당하는 일은 다음과 같다.

- 빈 생성 및 라이프사이클 관리
- 의존성 주입
- 설정 관리
- 부가 엔터프라이즈 기능 지원

## 2. 스프링 빈이란?

스프링 빈이란, 스프링 컨테이너가 직접 생성하고 관리하는 자바 객체를 말한다.

## 3. 어노테이션

어노테이션은 코드에 메타데이터(부가 정보)를 붙이는 문법으로, Java 5부터 도입됐다. 중요한 건 어노테이션 자체는 단지 표식일 뿐, 스스로는 아무 동작도 하지 않는다는 점이다.

예전에는 이런 메타데이터를 XML 설정 파일이나 네이밍 컨벤션, 마커 인터페이스 등으로 표현했다. 

XML은 코드와 분리되어 있어서 코드가 바뀔 때마다 같이 관리해야 하는 번거로움이 있었고, 
마커 인터페이스는 실제 기능과 무관한 상속 관계를 억지로 만들어야 한다는 문제가 있었다. 

어노테이션은 이 문제들을 해결하면서, 컴파일 타임 검사와 런타임 리플렉션을 둘 다 활용할 수 있다는 장점이 있다.

**어노테이션 정의**는 이렇게 한다.

```java
public @interface MyAnnotation {
    String value() default "";
    int count() default 1;
}
```

interface가 아니라 `@interface`를 쓰고, 내부에 속성을 선언할 수 있으며, `default` 키워드로 기본값도 줄 수 있다.

**메타 어노테이션**도 있다. 어노테이션 정의부에 또 다른 어노테이션을 붙여서 여러 설정을 할 수 있는 것인데, `@RestController`의 정의를 보면 이해가 쉽다.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller
@ResponseBody
public @interface RestController {

	@AliasFor(annotation = Controller.class)
	String value() default "";

}
```

- `@Retention` — 생존 범위를 정한다. `SOURCE`는 소스코드에만 존재하다 컴파일 시 사라지고(`@Override` 등), `CLASS`는 `.class` 파일까지는 남지만 런타임엔 JVM이 로드하지 않으며(기본값이지만 거의 안 씀), `RUNTIME`은 런타임까지 살아남아서 리플렉션으로 읽을 수 있다(스프링이 정의하는 대부분의 어노테이션이 여기 해당).
- `@Target` — 붙일 수 있는 위치를 정한다. `TYPE`(클래스/인터페이스/enum), `METHOD`, `FIELD`, `PARAMETER`, `CONSTRUCTOR` 등이 있다.
- `@Documented` — Javadoc에 포함시킬지 여부
- `@Inherited` — 이게 붙은 어노테이션 A가 부모 클래스에 붙어있으면, 자식 클래스에 A가 없어도 자동으로 붙은 것처럼 처리해준다.
- `@Repeatable`(Java 8+) — 같은 어노테이션을 한 곳에 여러 번 붙일 수 있게 해준다(`@Scheduled`가 대표적).

`@RestController` 정의에 `@Controller`, `@ResponseBody`가 같이 붙어있는 것도 눈여겨볼 만하다. 이 메타 어노테이션들은 런타임에 스프링의 `AnnotatedElementUtils`가 해석해서, `@RestController`가 붙은 클래스에 `@Controller`와 `@ResponseBody`를 각각 붙여둔 것처럼 동작하게 만들어준다. 참고로 `@AliasFor(annotation = Controller.class)`는 이 속성과 이름이 같은 `Controller`의 속성에 값을 자동으로 동기화해주는 역할이다(이름이 다르면 `attribute`로 대상 속성명을 따로 지정해야 한다).

## 4. 빈 등록 과정 2가지

빈을 등록하는 방법은 크게 설정 파일을 이용하는 방법과, 컴포넌트 스캔을 통해 자동으로 등록하는 방법 두 가지가 있다.

### 설정 파일로 빈 등록하기

```java
@Configuration
public class AppConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentService();
    }

    @Bean
    public OrderService orderService(PaymentService paymentService) {
        // 다른 빈을 파라미터로 받으면, 스프링이 알아서 위에서 등록한 paymentService 빈을 주입해줌
        return new OrderService(paymentService);
    }
}
```

`@Configuration`을 붙여 설정 클래스를 만든다. `@Configuration` 안에는 `@Component`가 메타 어노테이션으로 붙어있기 때문에, 이 클래스 자체도 컴포넌트 스캔의 대상이 된다. 그리고 메서드 위에 `@Bean`을 붙이면, 그 메서드의 리턴값을 스프링이 빈으로 등록해준다.

이 방식은 상황에 따라 나중에 구현체를 교체해야 할 때, 혹은 소스코드를 직접 수정할 수 없는 외부 라이브러리 클래스를 빈으로 등록해야 할 때 유용하다. XML로 설정 파일을 작성하는 방식도 있지만 요즘은 잘 쓰지 않아서 생략한다.

### 컴포넌트 스캔으로 빈 자동 등록하기

전체 흐름을 먼저 잡고 들어가면 이렇다.

```
1. 설정 클래스 파싱 - 어디부터 스캔할지 알아내기
2. 클래스패스에서 후보 찾기 - 클래스 찾아내기
3. BeanDefinition 등록
4. 실제 인스턴스화 (컨테이너 refresh()시점)
```

핵심은 1~3(스캔 단계)와 4(생성 단계)가 분리되어 있다는 점이다. 만약 스캔하면서 바로바로 인스턴스화한다면, A가 아직 스캔되지 않은 B를 의존하는 상황에서 문제가 생긴다. 그래서 "스캔"과 "생성"을 분리해서, 스캔 단계에서 일단 전부 등록해둬야 어떤 순서로 빈을 만들든 서로의 의존관계를 다 찾을 수 있는 구조가 된다. 각 단계를 하나씩 따라가 봤다.

**1단계. 설정 클래스 파싱 — `@ComponentScan`은 누가 읽는가**

`main()`이 실행되고 `SpringApplication.run(MyApp.class, args)`가 호출되면, 컨테이너는 아직 아무 정보도 없는 상태라 가장 먼저 리플렉션으로 `MyApp` 클래스에 뭐가 붙어있는지 확인한다. `@SpringBootApplication`을 열어보면 그 안에 `@ComponentScan`이 메타 어노테이션으로 붙어있다.

여기서 "`basePackages`가 명시 안 돼 있는데?" 싶었는데, 명시가 없으면 `@SpringBootApplication`이 붙은 클래스가 속한 패키지를 스캔 시작점으로 기본 사용한다. 그래서 이 클래스를 프로젝트 최상단 패키지에 둬야 하는 것이었다.

```
1. main() 실행 → SpringApplication.run(MyApp.class, args)
2. 컨테이너: "MyApp에 뭐가 붙어있지?" → 리플렉션으로 확인
3. @SpringBootApplication 발견 → 메타 어노테이션 @ComponentScan 발견
4. basePackages 확인 → 명시 안 됐으면 MyApp이 있는 패키지를 기본값으로 사용
5. 스캔 범위 확정 → 실제 스캔 시작 (ASM 파싱 단계로 진입)
```

이 과정을 실제로 담당하는 게 `ConfigurationClassPostProcessor`다. 이건 `BeanFactoryPostProcessor`의 한 종류인데, 일반적인 빈들은 4단계(실제 인스턴스화)에서 생성되는 반면 `BeanFactoryPostProcessor`는 그보다 훨씬 먼저, 다른 빈들이 생성되기도 전에 실행된다. 역할 자체가 "어떤 빈들을 등록할지 결정하는 것"이기 때문이다.

`ConfigurationClassPostProcessor`가 하는 일은:

1. `@Configuration`(또는 `@SpringBootApplication`)이 붙은 클래스를 찾는다.
2. 리플렉션으로 열어서 `@ComponentScan`, `@Import`, `@Bean` 메서드 등이 있는지 확인한다.
3. `@ComponentScan`을 발견하면 `basePackages`(없으면 이 클래스가 속한 패키지)를 꺼내서 2단계로 넘긴다.

이 분석을 실제로 수행하는 건 `ConfigurationClassParser`다. 이 클래스가 설정 클래스를 파싱하면서, `@ComponentScan`이 있으면 스캔 프로세스를 트리거하고, `@Bean` 메서드가 있으면 그 메서드 자체를 하나의 빈 정의로 등록하고, `@Import`가 있으면 다른 설정 클래스를 재귀적으로 파싱한다. 여기서는 그중 `@ComponentScan` 경로만 따라가 본 것이다.

**2단계. 클래스패스에서 후보 찾기 — 왜 굳이 ASM으로 읽나**

"com.example 패키지 밑을 뒤져라"는 명령이 떨어지고 나면, 단순하게 모든 클래스를 `Class.forName()`으로 로딩해서 확인하면 안 되나? 하는 생각이 들 수 있다. Java에서 클래스를 로딩(`ClassLoader`가 `.class` 파일을 읽어 JVM 메모리에 올리는 것)하면 static 초기화 블록이 실행되고 클래스 전체 구조가 메모리에 올라간다. 프로젝트에 클래스가 수백~수천 개라면 "일단 다 로딩해보고 어노테이션이 있나 확인하자"는 방식은 매우 느릴 수밖에 없다.

그래서 쓰는 게 **ASM**이다. ASM은 `.class` 파일(바이트코드)을 JVM에 로딩하지 않고도 직접 파싱할 수 있게 해주는 저수준 라이브러리다. 실제 스캔은 `ClassPathScanningCandidateComponentProvider`가 담당하는데, 흐름은 이렇다.

- `basePackages`를 패키지 경로에서 리소스 경로(`classpath*:com/example/**/*.class`)로 변환
- `PathMatchingResourcePatternResolver`로 해당 경로의 `.class` 파일을 전부 찾는다(아직 로딩은 안 함)
- 각 `.class` 파일을 ASM으로 읽어서, `SimpleMetadataReader`가 클래스 이름과 어노테이션 정보만 추출
- `TypeFilter`(기본은 `AnnotationTypeFilter`)로 걸러내서 `@Component`이거나 이를 메타 어노테이션으로 가진 것들만 후보로 채택

이렇게 걸러진 클래스 하나하나가 `ScannedGenericBeanDefinition` 객체로 만들어진다. 이 시점엔 "이런 클래스가 있고, 이런 어노테이션이 붙어있다"는 메타데이터만 존재할 뿐, 실제 인스턴스는 아직 없다.

**3단계. BeanDefinition 등록**

스캔된 후보들은 `ClassPathBeanDefinitionScanner`(스캔 담당 클래스를 상속해서 등록 기능까지 갖춘 클래스)를 통해 다음 과정을 거친다.

1. **빈 이름 결정** — `AnnotationBeanNameGenerator`가 `@Component("customName")`처럼 이름이 명시돼 있으면 그걸 쓰고, 없으면 클래스 이름의 첫 글자를 소문자로 바꿔서 생성한다(`UserService` → `userService`).
2. **스코프 결정** — `@Scope` 어노테이션이 있으면 반영, 없으면 기본값 `singleton`.
3. **중복 체크** — `checkCandidate()`가 같은 이름의 빈이 이미 등록돼 있는지 확인하고, 충돌하면 예외를 던진다.
4. 최종적으로 `BeanDefinitionRegistry.registerBeanDefinition(name, beanDefinition)`을 호출해서, 컨테이너 내부의 빈 정의 저장소(`DefaultListableBeanFactory` 안의 `Map<String, BeanDefinition> beanDefinitionMap`)에 등록한다.

**4단계. 실제 인스턴스화 (컨테이너 `refresh()` 시점)**

3단계까지는 사실 "설계도만 그려놓은 상태"고, 아직 어떤 객체도 생성되지 않았다. `AbstractApplicationContext.refresh()`가 전체 부팅 과정을 오케스트레이션하는데, 그 뒷부분 `finishBeanFactoryInitialization()`에서 등록된 모든 싱글톤 빈을 실제로 생성한다(`preInstantiateSingletons()`).

각 빈마다 다음 일이 일어난다.

1. **생성자 결정** — 생성자가 여러 개면 `@Autowired`가 붙은 생성자를 우선 사용하고, 없으면 기본 생성자나 유일한 생성자를 쓴다.
2. **의존성 해결** — 생성자 파라미터 타입을 보고 컨테이너에서 일치하는 빈을 찾아온다. 타입이 여러 개면 `@Qualifier`/`@Primary`가 여기서 작동한다. 이 과정에서 의존하는 빈이 아직 생성 안 됐으면 그 빈을 먼저 재귀적으로 생성하는데, 이게 순환 참조 문제가 생기는 지점이기도 하다.
3. **객체 생성** — 생성자를 리플렉션(`Constructor.newInstance()`)으로 호출해서 인스턴스를 만든다.
4. **필드/세터 주입** — 생성자 주입 후 남은 `@Autowired` 필드나 세터가 있으면 `AutowiredAnnotationBeanPostProcessor`(이것도 `BeanPostProcessor`의 한 종류)가 처리한다.
5. **초기화 콜백** — 뒤에서 정리한 빈 라이프사이클대로 `@PostConstruct` 등이 실행된다.
6. **싱글톤 캐시에 저장** — 완성된 빈이 `singletonObjects`에 저장되고, 이후 이 빈을 요청하면 새로 만들지 않고 이 캐시에서 꺼내준다.

스프링이 빈을 기본으로 싱글톤 등록하는 이유도 여기서 다시 확인할 수 있었다. Service, Repository, Controller 등은 대부분 상태가 없는 클래스라, 요청마다 달라지는 데이터를 필드에 기억하지 않는다. 그러니 매번 생성해서 메모리를 낭비할 필요 없이 여러 스레드가 동시에 공유해서 쓰는 게 유리하다. 매번 새 인스턴스가 필요한 특별한 경우에만 `@Scope("prototype")`(또는 `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)`)로 예외를 둔다.

**전체 흐름을 한 번에 그려보면:**

```
[부팅]
  ↓
ConfigurationClassPostProcessor 동작
  ↓
ConfigurationClassParser가 @ComponentScan 발견
  ↓
ClassPathScanningCandidateComponentProvider가 클래스패스 스캔 (ASM으로 가볍게)
  ↓
@Component 계열 어노테이션 붙은 클래스만 후보로 필터링
  ↓
ScannedGenericBeanDefinition 생성 (아직 메타데이터뿐, 인스턴스 없음)
  ↓
BeanDefinitionRegistry에 등록
  ↓
[컨테이너 refresh() 진행 중, finishBeanFactoryInitialization()]
  ↓
등록된 BeanDefinition마다: 생성자 결정 → 의존성 해결 → 인스턴스 생성
  ↓
AutowiredAnnotationBeanPostProcessor로 필드/세터 주입
  ↓
초기화 콜백 (@PostConstruct 등)
  ↓
싱글톤 캐시에 저장, 사용 준비 완료
```

## 5. 빈 라이프사이클

빈은 만들어졌다고 바로 끝이 아니라, 아래와 같은 단계를 거쳐서 "완성된 빈"이 된다.

```
컨테이너 시작
   ↓
[1] BeanDefinition 등록 (스캔 과정)
   ↓
[2] 인스턴스 생성 (생성자 호출)
   ↓
[3] 의존성 주입 (필드/세터)
   ↓
[4] Aware 인터페이스 콜백
   ↓
[5] 초기화 전 후처리 (BeanPostProcessor - Before)
   ↓
[6] 초기화 콜백 (@PostConstruct 등)
   ↓
[7] 초기화 후 후처리 (BeanPostProcessor - After)
   ↓
   → 완성된 빈, 사용 준비 완료 ←
   ↓
[8] (컨테이너 종료 시) 소멸 콜백 (@PreDestroy 등)
```

객체를 `new`하고 나면 바로 다 쓸 수 있는 상태 아닌가 싶었는데, 굳이 초기화 단계를 따로 두는 이유가 있었다. 생성자는 필드에 값을 채워 넣는 것까지만 책임지는 게 자연스러운데, 그 값들이 다 채워진 이후에만 할 수 있는 작업들이 있기 때문이다. 예를 들어 생성자에서 커넥션 풀을 주입받았는데 그 풀로 "초기 연결 테스트"를 하고 싶다면, 이건 생성자 안에서 하기 애매하다(다른 의존성들이 아직 다 안 채워졌을 수도 있고, 생성 책임과 초기화 책임을 분리하는 게 관례상 깔끔하다). 그래서 스프링은 "다 조립된 후에 실행되는" 별도의 초기화 단계를 만들어둔 것이다.

**각 단계를 좀 더 들여다보면:**

- **[2] 인스턴스 생성** — 리플렉션의 `Constructor.newInstance()`가 여기서 실행된다. 생성자 주입이 아니라면 이 시점엔 필드가 아직 비어있을 수 있다.
- **[3] 의존성 주입** — 생성자 주입은 [2]와 동시에 일어나고(생성자 파라미터로 넘겨지므로), 필드/세터 주입은 객체가 이미 만들어진 뒤 `AutowiredAnnotationBeanPostProcessor`가 리플렉션(`Field.set()`)으로 채워 넣는다.
- **[4] Aware 인터페이스 콜백** — 빈이 "컨테이너 자체의 정보"가 필요할 때 쓰는 특수 콜백이다. `BeanNameAware`(내 빈 이름이 뭔지), `ApplicationContextAware`(컨테이너 자체에 접근하고 싶을 때) 등이 있다. 나를 관리하는 컨테이너에 대한 정보가 필요한 특수 케이스를 위한 통로라고 보면 된다.
- **[5][7] `BeanPostProcessor`** — 모든 빈의 초기화 전/후에 공통으로 끼어들 수 있는 확장 지점이다. 이게 중요한 이유는, 스프링 AOP(예: `@Transactional`)가 바로 이 지점에서 동작하기 때문이다. `@Transactional`이 붙은 빈은 초기화 후 단계([7])에서 `BeanPostProcessor`가 원본 객체를 프록시 객체로 감싸서 교체한다. 그래서 `@Autowired`로 받는 서비스 빈은 사실 원본이 아니라 "트랜잭션 시작/커밋 로직이 앞뒤로 붙은 프록시"인 경우가 많다. `@Transactional`이 마법처럼 동작하는 이유가 여기 있었다.
- **[6] 초기화 콜백** — 세 가지 방법이 있고 우선순위 순으로 보면 `@PostConstruct`(표준 어노테이션, 가장 많이 씀) → `InitializingBean`의 `afterPropertiesSet()`(스프링 전용 인터페이스라 프레임워크에 코드가 종속됨, 요즘은 잘 안 씀) → `@Bean(initMethod = "커스텀메서드명")`(외부 라이브러리처럼 코드를 직접 못 건드릴 때). 셋 다 같은 시점에 실행되지만, 인터페이스 구현 방식은 스프링에 코드가 강하게 결합돼버려서(스프링 없이는 컴파일도 안 됨) 관례상 `@PostConstruct`를 쓴다.
- **[8] 소멸 콜백** — 컨테이너가 종료될 때(`ConfigurableApplicationContext.close()`) 호출된다. `@PreDestroy`가 표준이고, 커넥션 풀 종료나 리소스 해제에 쓰인다. 다만 이 소멸 콜백은 **싱글톤 스코프에서만** 컨테이너가 보장한다. 왜 그런지는 스코프 파트에서 이어서 정리했다.

## 6. 빈 스코프

스코프는 "이 빈을 요청할 때마다 같은 객체를 줄 것인가, 매번 새 객체를 줄 것인가"를 결정하는 설정이다.

### singleton (기본값)

컨테이너당 인스턴스가 1개다. 처음 요청 시(또는 컨테이너 시작 시 미리) 만들어져서 캐시(`singletonObjects` Map)에 저장되고, 이후 모든 요청이 같은 객체를 참조한다.

**왜 기본값인가** — 대부분의 빈(Service, Repository 등)은 상태를 갖지 않는(stateless) 로직 덩어리이기 때문이다. 로직만 담당하는 객체라면 굳이 매번 새로 만들 필요가 없고, 재사용하는 게 메모리·성능 면에서 훨씬 유리하다.

**주의할 점** — 싱글톤이기 때문에, 이 빈에 가변 상태(mutable field)를 두면 여러 요청/스레드가 그 필드를 공유하게 돼서 동시성 문제가 생긴다. 그래서 스프링 빈은 관례적으로 필드에 상태를 두지 않고, 메서드 파라미터나 지역 변수로만 데이터를 다룬다.

### prototype

요청할 때마다 매번 새 인스턴스를 생성한다. 컨테이너는 만들어서 넘겨주기만 하고, 그 이후 생명주기는 관리하지 않는다.

**왜 필요한가** — 상태를 가져야 하는 객체(요청마다 다른 값을 누적하는 객체 등)는 공유하면 안 되니 매번 새로 만들어야 한다.

**소멸 콜백이 자동 호출 안 되는 이유** — 컨테이너가 "이 프로토타입 빈을 누가 언제까지 쓰는지" 알 방법이 없기 때문이다. 싱글톤은 컨테이너 자신이 유일하게 참조를 들고 있으니 "컨테이너 종료 = 이제 안 쓴다"가 명확하지만, 프로토타입은 넘겨준 순간 컨테이너 손을 떠나서 누가 얼마나 오래 들고 있을지 알 수가 없다. 그래서 소멸 관리 책임도 넘겨받은 쪽(개발자)에게 넘어간다.

### singleton이 prototype을 주입받을 때 생기는 문제

```java
@Component
@Scope("singleton")
class OrderService {
    private final CartHolder cartHolder; // prototype 빈

    OrderService(CartHolder cartHolder) {
        this.cartHolder = cartHolder;
    }
}
```

`OrderService`는 싱글톤이라 딱 한 번만 생성된다. 그 한 번의 생성 시점에 `CartHolder`가 주입되고 나면, 그 이후로는 `OrderService`가 살아있는 내내 그 최초의 `CartHolder` 인스턴스 하나만 계속 참조하게 된다. `CartHolder`를 프로토타입으로 만든 의도(요청마다 새로 갖고 싶었던 것)가 완전히 무력화되는 것이다.

**해결책** — `ObjectProvider<CartHolder>`(또는 `Provider<CartHolder>`)를 대신 주입받아서, 필요한 시점마다 `.getObject()`를 호출하도록 하면 그때그때 새 프로토타입 인스턴스를 받아올 수 있다.

```java
@Component
class OrderService {
    private final ObjectProvider<CartHolder> cartHolderProvider;

    OrderService(ObjectProvider<CartHolder> cartHolderProvider) {
        this.cartHolderProvider = cartHolderProvider;
    }

    void process() {
        CartHolder cartHolder = cartHolderProvider.getObject(); // 호출 시점마다 새로 받아옴
    }
}
```

"주입은 생성 시점에 한 번만 일어난다"는 원리를 이해해야, 왜 이게 문제이고 왜 이 해법이 통하는지 납득이 되는 부분이었다.

### 웹 전용 스코프 (request / session / application)

- **request** — HTTP 요청 하나당 인스턴스 1개(요청 끝나면 소멸)
- **session** — 사용자 세션 하나당 인스턴스 1개
- **application** — `ServletContext` 하나당 인스턴스 1개(사실상 싱글톤과 비슷하지만 웹 컨텍스트 단위)

이것들도 결국 "생명주기를 컨테이너가 아니라 HTTP 요청/세션의 생명주기에 맞춘 것"일 뿐이라, singleton/prototype과 원리는 같다. 언제 만들고 언제 버릴지를 정하는 기준만 다른 것이다.

### 라이프사이클과 스코프를 잇는 한 문장

라이프사이클은 "빈 하나가 태어나서 죽을 때까지 거치는 단계"를, 스코프는 "그 빈이 몇 개나 존재하고 언제 태어나고 죽는지 그 범위"를 결정한다. 즉 스코프가 "라이프사이클이 얼마나 자주 반복되는가"를 정하는 상위 설정이라고 보면 둘의 관계가 명확해진다 — singleton이면 라이프사이클이 딱 한 번만 도는 것이고, prototype이면 요청할 때마다 [2]~[6] 단계가 매번 새로 도는 것이다(단 [8]은 돌지 않는다).

### 스프링 컨테이너 내부 동작 원리

지금까지 다룬 라이프사이클·스코프가 실제 컨테이너 내부에서는 크게 3단계 메커니즘으로 동작한다.

1. **빈 메타정보 추상화(`BeanDefinition`)** — 컨테이너는 자바 설정 클래스(`@Configuration`), 애노테이션(`@Component`), XML 등 설정 형식에 종속되지 않는다. 어떤 방식이든 설정을 읽어 `BeanDefinition`이라는 일관된 메타데이터 객체로 변환한다. 여기엔 빈의 클래스 타입, 스코프(싱글톤/프로토타입), 지연 로딩 여부, 생성자 인자 정보 등이 담긴다.
2. **싱글톤 객체 저장소(`DefaultSingletonBeanRegistry`)** — 생성된 싱글톤 객체들은 `ConcurrentHashMap`에 보관된다.
    
    ```java
    // 스프링 내부 싱글톤 캐시 저장소
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    ```
    
    빈 요청(`getBean()`)이 들어오면 먼저 이 캐시 맵에서 조회하고, 없을 경우에만 리플렉션으로 객체를 생성해 맵에 캐싱한 뒤 반환한다.
    
3. **빈 후처리기(`BeanPostProcessor`)와 AOP 프록시** — 객체가 생성된 직후, 컨테이너는 등록된 `BeanPostProcessor`들을 순회하며 객체를 조작할 기회를 준다. `@Autowired` 주입 처리(`AutowiredAnnotationBeanPostProcessor`)나, `@Transactional` 같은 AOP 기능이 적용될 때 실제 객체 대신 CGLIB 기반 다이내믹 프록시 객체를 가로채서 빈 저장소에 등록하는 과정이 모두 이 내부 파이프라인에서 일어난다.

---
---

## 1. MVC 패턴 vs Spring MVC

MVC(Model-View-Controller)는 소프트웨어를 세 가지 역할로 나누는 설계 패턴이다. 각각이 의미하는 바는 다음과 같다.

- Model — 데이터와 비즈니스 로직 (예: 도메인 객체, DB 처리)
- View — 사용자에게 보여지는 화면 (예: HTML, UI)
- Controller — 사용자 입력을 받아서 Model을 조작하고, 적절한 View를 선택해서 응답하는 중개자

핵심 목적은 각 계층의 관심사를 분리(Separation of Concerns)하는 것이다.

Spring MVC는 이 MVC 패턴을 스프링이 실제 코드로 구현해놓은 프레임워크다. Spring MVC에서 Model, View, Controller는 각각 다음에 해당한다.

- Model : Service, Entity, Repository 등
- View : Thymeleaf, JSP 같은 템플릿. REST API라면 JSON으로 직렬화된 응답
- Controller : `@Controller`, `@RestController`가 붙은 클래스, 혹은 그 안의 `@GetMapping`된 핸들러 메서드

## 2. Servlet이란? 웹 요청은 어떻게 처리되는가

클라이언트가 `GET /users` 같은 HTTP 요청을 보내면, 서버는 그걸 받아서 처리하고 응답을 돌려줘야 한다. 이 "받아서 처리하고 응답한다"를 어떤 코드가 담당해야 하는데, Java 진영에서 그 역할을 표준화한 게 바로 Servlet이다.

즉 Servlet은 자바로 작성된, "HTTP 요청을 받아서 처리하는 방법"을 정의하는 표준 인터페이스(명세)다. Java EE(현재는 Jakarta EE) 스펙 중 하나이고, 핵심은 개발자가 직접 소켓을 열고 HTTP를 파싱하고 응답을 만드는 저수준 작업을 대신해준다는 점이다. 요청이 오면 `service()`가 호출될 테니, 처리 로직만 잘 작성하도록 틀을 제공하는 셈이다.

실제 클라우드 환경(예: AWS EC2)에 배포했을 때, 요청 하나가 처리되는 과정을 단계별로 따라가 보면 이렇다.

1. **클라이언트 → Nginx** — 사용자가 도메인으로 접속하면 가장 먼저 Nginx가 80/443 포트에서 요청을 받는다.
2. **SSL 처리** — Nginx가 Certbot 등으로 발급받은 인증서를 적용해서 암호화/복호화를 전담한다(SSL Termination).
3. **Nginx → Tomcat 포워딩** — 정적 파일(이미지, CSS 등)은 Nginx 선에서 바로 응답해버리고, 톰캣까지 갈 필요가 없다. 반면 DB 조회가 필요한 동적 요청(API 등)은 "이건 내가 못해, 톰캣 네가 처리해"라며 내부망 8080 포트로 넘긴다(리버스 프록시). 외부 사용자는 이 내부 포트를 전혀 알 수 없기 때문에 보안도 강화된다.
4. **Tomcat: HTTP 파싱** — 넘겨받은 HTTP 텍스트 스트림을 파싱해서 `HttpServletRequest`/`HttpServletResponse` 객체로 바꾸고, 요청 처리용 스레드를 하나 배정한다.
5. **Tomcat → DispatcherServlet** — 만들어진 `HttpServletRequest`를 파라미터로 넘기면서 `DispatcherServlet.service()`를 호출한다. 이건 네트워크 통신이 아니라 **WAS 내부에서 일어나는 자바 메서드 호출**이라는 게 처음엔 헷갈렸는데, 결국 톰캣과 스프링이 같은 프로세스 안에 있어서 가능한 일이었다.
6. **DispatcherServlet → Controller** — 스프링 컨테이너 내부의 HandlerMapping을 조회해서 알맞은 `@Controller`로 요청을 라우팅한다.
7. **Controller → Service → Repository** — 개발자가 작성한 비즈니스 로직이 실행되고, 필요하면 Repository를 통해 DB(RDS 등)와 통신한다.
8. **응답 역순 전달** — 처리 결과가 Tomcat → Nginx를 거쳐 최종적으로 클라이언트에게 전달된다.

정리하자면, 무거운 작업(SSL 처리, 정적 파일 응답, 보안 필터링)은 앞단의 Nginx가 다 걸러주고, 가볍게 정제된 핵심 요청만 뒤쪽의 스프링 부트(내장 톰캣)에게 넘겨서 처리하게 만드는 게 현대 백엔드 아키텍처의 정석이라는 걸 알 수 있었다.

## 3. 톰캣이란? WAS란?

WAS는 동적 컨텐츠를 만들어서 응답하는 서버다. 요청에 따라 비즈니스 로직을 실행하고, DB를 조회하고, 그 결과를 조합해서 응답을 만들어낸다(Tomcat, Jetty, Undertow 등). Java 진영에서 WAS는 보통 Servlet을 실행할 수 있는 환경을 의미한다. 이미 만들어진 정적 콘텐츠(HTML, CSS, JS, 이미지 등)를 요청대로 그대로 전달해주는 Web Server(Nginx, Apache HTTP Server 등)와는 반대되는 개념으로 이해하면 된다.

WAS, 서블릿 컨테이너, 톰캣의 관계를 정리하면 이렇다. WAS가 가장 넓은 개념이고, 그 안에서 핵심 역할을 하는 부품이 서블릿 컨테이너이며, 이 서블릿 컨테이너를 구현한 구현체 중 하나가 톰캣이다.

- WAS — 동적 웹 애플리케이션을 실행할 수 있는 전체 환경을 제공
- 서블릿 컨테이너 — 자바 서블릿의 생명주기를 관리하고 클라이언트의 요청/응답을 매핑하는, 서블릿을 담고 실행해주는 통. Servlet은 인터페이스일 뿐이라 이걸 실제로 실행해줄 런타임 환경이 필요한데, 그게 서블릿 컨테이너다.
- 톰캣 — 서블릿 컨테이너 기능을 무료로 구현해놓은 아파치 재단의 오픈소스 소프트웨어

톰캣이 구체적으로 하는 일은 다음과 같다.

- 네트워크 계층 처리 — 소켓을 열고 TCP 연결을 받아들인다.
- HTTP 파싱 — 들어온 바이트 스트림을 파싱해서 `HttpServletRequest` 객체로 변환한다.
- 스레드 관리 — 요청 하나당 스레드를 하나씩 배정해서 동시에 여러 요청을 처리한다(스레드 풀 관리).
- Servlet 생명주기 관리 — 생성(`init()`) → 요청마다 `service()` 호출 → 종료 시 `destroy()`.
- 라우팅 — 요청 URL에 매핑된 Servlet을 찾아서 `service()`를 호출한다.
- 응답 변환 — Servlet이 채운 `HttpServletResponse`를 다시 HTTP 응답 형식으로 바꿔서 클라이언트에게 보낸다.

스프링 컨트롤러를 작성할 때 소켓 열기, HTTP 파싱, 스레드 배정 같은 걸 전혀 신경 쓰지 않아도 되는 이유가 바로 이 톰캣 덕분이다.

여기서 좀 더 파보니, 스프링은 결국 톰캣 "안에서" 돌아가는 구조라는 걸 알게 됐다. 서버에서 실제로 실행되는 단일 프로세스는 JVM 위에서 구동되는 톰캣이고, 스프링 프레임워크(스프링 컨테이너, DispatcherServlet, Controller 등)는 독립적인 프로세스가 아니라 톰캣 프로세스가 할당받은 Heap 메모리 내부에 인스턴스화되어 있는 자바 객체들의 집합일 뿐이다.

제어권 흐름으로 봐도 마찬가지다. 톰캣이 서버 포트(예: 8080)를 열고 리스닝을 시작하고, 요청이 들어오면 스레드 풀에서 스레드를 꺼내 할당하고, HTTP 텍스트를 `HttpServletRequest` 객체로 파싱한 뒤, 자신의 메모리 위에 올라가 있는 스프링의 `DispatcherServlet.service()`를 호출한다. 이 시점부터 스프링 코드가 그 스레드를 타고 실행되는 것이다. 즉 실행 환경을 제공하는 호스트는 톰캣이고, 그 위에서 동작하는 애플리케이션이 스프링이다.

배포 방식도 이 관계를 잘 보여준다. 전통적인 방식(WAR 배포)에서는 서버에 톰캣을 먼저 설치하고, 개발한 스프링 프로젝트를 `.war`로 압축해서 톰캣의 `webapps` 폴더에 밀어넣어야 실행됐다. 반면 스프링 부트는 이 과정을 간소화하려고 톰캣을 라이브러리 형태로 애플리케이션 안에 통째로 내장시켰다(`spring-boot-starter-web` 안에 포함).

```
전통 방식     : Tomcat(서버) 안에 → 내 애플리케이션(.war)을 넣음
스프링 부트   : 내 애플리케이션(.jar) 안에 → Tomcat이 내장되어 있음
```

그래서 `java -jar myapp.jar` 실행 한 줄이면 애플리케이션과 톰캣이 하나의 프로세스로 함께 뜬다. `SpringApplication.run()`을 호출하면 내부적으로 내장 톰캣을 띄우는 코드가 먼저 실행되고, 그 톰캣이 다 뜬 다음에야 `DispatcherServlet`이 그 위에 등록되어 요청을 받을 준비를 한다. 배포 형태는 다르지만 톰캣 위에서 스프링이 동작한다는 본질적인 구조는 결국 동일하다.

참고로 톰캣(WAS) 안에 스프링 컨테이너가 들어있다고 해서 이 전체를 WS(웹 서버)라고 부르지는 않는다. WS는 톰캣 앞단에서 정적 파일 처리와 트래픽 분산을 담당하는 Nginx 같은 것들을 가리키는 별도의 용어로 여전히 쓰인다.

## 4. DispatcherServlet은 무엇이고 어떻게 동작하는가

순수 Servlet 방식은 URL 하나당 Servlet 클래스를 하나씩 만들어야 해서 관리가 힘들었다. Spring MVC는 이걸 해결하려고 Servlet을 딱 하나만 만들고, 그 안에서 모든 요청을 알아서 적절한 메서드로 분배하는 전략을 택했는데, 이게 Front Controller 패턴이고 그 구현체가 DispatcherServlet이다. MVC 패턴 자체에는 없는, Spring MVC만의 구조라는 점이 흥미로웠다.

**클래스 계층부터 보면** DispatcherServlet의 정체가 좀 더 명확해진다.

```
DispatcherServlet
  extends FrameworkServlet
    extends HttpServletBean
      extends HttpServlet   ← 진짜 Servlet 인터페이스 구현체
```

결국 DispatcherServlet도 HttpServlet을 상속한 그냥 평범한 Servlet 하나다. 톰캣 입장에서는 등록된 여러 Servlet 중 하나일 뿐이다. 다만 이 Servlet이 **모든 URL(`/`)에 매핑**되어 있어서 사실상 모든 요청을 독점적으로 받는다는 게 특별한 점이다. 전통 방식이면 `web.xml`에 직접 매핑을 등록해야 하지만, 스프링 부트에서는 `DispatcherServletAutoConfiguration`이 이 등록을 자동으로 해준다.

**요청이 도착해서 실제로 `doDispatch()`까지 가는 과정**도 따라가 봤는데, 처음엔 "`HttpServlet.service()` → `doGet()`/`doPost()` 분기 → `FrameworkServlet`이 오버라이드한 `doService()`로 모인다" 정도로만 이해했는데, 실제 소스를 열어보니 한 단계 더 복잡했다.

```
HttpServlet.service()
 → FrameworkServlet.service()  (PATCH면 바로 processRequest(), 아니면 부모 호출)
 → HttpServlet.service()의 Method별 분기 → FrameworkServlet.doGet()/doPost()/...
 → processRequest() → DispatcherServlet.doService() → doDispatch()
```

PATCH만 유독 따로 취급하는 이유가 있었는데, PATCH는 원래 HTTP 표준에 없다가 2010년에 뒤늦게 추가된 메서드라 오래된 표준 HttpServlet에는 애초에 `doPatch()`가 없기 때문이다. 그래서 PATCH 요청은 표준 분기를 탈 수가 없고, 스프링이 `FrameworkServlet` 단계에서 자체적으로 가로채 처리하도록 만들어둔 것이었다. 결국 GET이든 POST든 PATCH든 어떤 경로를 거치든 전부 `processRequest()` → `doService()`로 수렴하는데, HTTP 메서드가 뭐든 "요청 → 핸들러 찾기 → 실행 → 응답 만들기"라는 처리 흐름 자체는 동일하기 때문에 이렇게 모아두는 게 자연스럽다.

**`doDispatch()` 내부는 단계별로 이렇게 진행된다.**

1. 멀티파트 요청인지 체크한다. 파일 업로드(`multipart/form-data`)면 `MultipartResolver`가 요청을 특수하게 감싸서 이후 단계에서 파일 파라미터를 편하게 다룰 수 있게 준비한다.
2. `HandlerMapping`으로 "이 URL, 누가 처리해?"를 찾는다. 애플리케이션이 뜰 때 `RequestMappingHandlerMapping`이 모든 `@Controller`/`@RestController` 빈을 스캔해서 "URL 패턴 → 실행할 메서드" 매핑 테이블을 미리 만들어두고, 요청이 오면 이 테이블에서 맞는 걸 찾아 `HandlerExecutionChain`(핸들러 메서드 + 인터셉터들)을 반환한다.
3. `HandlerAdapter`로 "그 핸들러를 어떻게 실행하지?"를 해결한다. 처음엔 "핸들러를 찾았으면 그냥 실행하면 되지 왜 또 어댑터가 필요하지" 싶었는데, 이유는 핸들러마다 파라미터 개수·타입·리턴 타입이 다 제각각이기 때문이었다. `RequestMappingHandlerAdapter`가 파라미터를 하나씩 보고 필요한 값을 꺼내 바인딩한 뒤, 메서드를 리플렉션으로 호출하고, 리턴값을 `ModelAndView`로 표준화해서 돌려준다.
4. 인터셉터들의 `preHandle()`이 순서대로 실행된다(인증 체크, 로깅 등을 여기서 많이 한다).
5. 찾은 어댑터를 통해 실제 `@GetMapping` 메서드가 실행된다. `@ResponseBody`(또는 `@RestController`)라면 리턴값을 `HttpMessageConverter`가 JSON으로 직렬화해서 바로 응답 바디에 쓰고 아래 단계는 건너뛴다. View 이름(문자열)을 리턴하는 전통적 방식이면 `ModelAndView`로 감싸져서 다음 단계로 넘어간다.
6. 인터셉터의 `postHandle()`이 실행된다. 핸들러 실행은 끝났지만 View 렌더링 전 시점이다.
7. View를 쓰는 경우, `ViewResolver`가 리턴된 View 이름(예: `"userDetail"`)을 실제 템플릿 파일(`userDetail.html`)과 매칭시켜 `View` 객체를 만든다.
8. 찾은 `View` 객체가 Model 데이터를 가지고 실제 HTML을 생성해서 `HttpServletResponse`에 쓴다.
9. 인터셉터의 `afterCompletion()`이 실행된다. 예외 발생 여부와 무관하게 실행되는 마무리 단계다.
10. 흐름 어디서든 예외가 나면 `HandlerExceptionResolver`(`@ExceptionHandler`, `@RestControllerAdvice` 처리 로직 포함)가 잡아서 적절한 응답으로 변환한다.

이 흐름을 다이어그램으로 그려보면 아래와 같다.

```
[Tomcat] → HttpServletRequest 생성
        → HttpServlet.service() → FrameworkServlet.service() (PATCH 분기)
        → Method별 분기 → FrameworkServlet.doGet()/doPost()/...
        → processRequest() → DispatcherServlet.doService()
                                              ↓
                                    doDispatch() 시작
                (1) 멀티파트 체크
                (2) HandlerMapping → 어느 컨트롤러 메서드?
                (3) HandlerAdapter → 그 메서드를 어떻게 실행?
                (4) Interceptor.preHandle()
                (5) 핸들러(컨트롤러 메서드) 실제 실행
                        ├─ @ResponseBody → JSON 변환 → 바로 응답
                        └─ View 이름 리턴 → 계속 진행
                (6) Interceptor.postHandle()
                (7) ViewResolver → View 찾기
                (8) View 렌더링 → 응답 완성
                (9) Interceptor.afterCompletion()
                                              ↓
                    HttpServletResponse 완성 → Tomcat이 HTTP 응답으로 변환 → 클라이언트 전송
```

여기서 반복해서 눈에 띈 설계 원칙이 하나 있는데, **역할마다 별도의 객체를 두고 DispatcherServlet은 그 객체들을 "지휘"만 한다**는 점이다. 핸들러를 찾는 일은 `HandlerMapping`에게, 핸들러를 실행하는 방법은 `HandlerAdapter`에게, View를 찾는 일은 `ViewResolver`에게, 예외 처리는 `HandlerExceptionResolver`에게 위임한다. DispatcherServlet 자신은 세부 로직을 갖지 않고 "누구에게 무엇을 물어봐야 하는지"만 알고 각 단계를 순서대로 호출하는 오케스트레이터 역할만 한다. 덕분에 Thymeleaf 대신 다른 템플릿 엔진을 쓰고 싶으면 `ViewResolver` 구현체만 바꾸면 되고, DispatcherServlet 자체는 건드릴 필요가 없다.

마지막으로 이 도구들(`HandlerMapping`, `HandlerAdapter`, `ViewResolver` 등)이 언제 준비되는지도 궁금해서 찾아봤는데, 스프링은 컨테이너가 뜰 때 바로 다 준비해두는 게 아니라 **Lazy-Init 전략**을 쓴다. `DispatcherServlet`이 `init()`을 거치는 시점(첫 요청이 왔을 때, 또는 컨테이너 refresh 직후 `onRefresh()` 콜백)에 `initStrategies()`를 호출해서 필요한 것들을 한꺼번에 초기화하는 것이다. 스프링 애플리케이션의 첫 요청이 유독 느린 이유가 바로 여기 있었다.



