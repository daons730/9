@echo off
chcp 65001 >nul
echo ========================================================
echo  스마트 GIF 생성기 (Smart GIF Maker) GitHub 푸시 도우미
echo ========================================================
echo.

git status >nul 2>&1
if %errorlevel% neq 0 (
    echo [오류] Git 저장소가 감지되지 않았습니다.
    pause
    exit /b 1
)

echo GitHub에서 새로 생성한 빈 저장소의 주소를 입력해 주세요.
echo 예시: https://github.com/사용자아이디/저장소이름.git
echo.
set /p REPO_URL="저장소 주소 입력: "

if "%REPO_URL%"=="" (
    echo [취소] 주소가 입력되지 않아 종료합니다.
    pause
    exit /b 1
)

git remote remove origin >nul 2>&1
git remote add origin %REPO_URL%

echo.
echo [1/2] 원격 저장소 연결 완료: %REPO_URL%
echo [2/2] GitHub main 브랜치로 푸시를 진행합니다...
echo.

git branch -M main
git push -u origin main

if %errorlevel% equ 0 (
    echo.
    echo ========================================================
    echo  성공적으로 GitHub에 푸시되었습니다!
    echo ========================================================
    echo  이제 GitHub 저장소 웹페이지의 [Actions] 탭에서 
    echo  클라우드 빌드가 자동으로 실행되는 것을 확인하실 수 있습니다.
    echo.
) else (
    echo.
    echo [안내] 푸시 도중 문제가 발생했습니다.
    echo GitHub 로그인 상태 및 저장소 주소가 올바른지 확인해 주세요.
    echo.
)

pause

