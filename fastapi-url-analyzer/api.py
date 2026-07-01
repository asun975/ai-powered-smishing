from asyncio import sleep
from dotenv import load_dotenv
import os

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import requests

URLSCAN_BASE_URL = "https://urlscan.io/api/v1"
SET_RATE_LIMIT = False
load_dotenv()
api_key = os.getenv('API_KEY')
urlAnalyzer = FastAPI()

urlAnalyzer.add_middleware(
    CORSMiddleware,
    # Restrict in production
    allow_origins=["127.0.0.1"],
    # Android app client does not implement session cookies/JWT yet
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

class AnalyzeRequest(BaseModel):
    url: str


class AnalyzeResponse(BaseModel):
    url: str
    malicious: bool
    score: int
    uuid: str

class HealthResponse(BaseModel):
    message: str

def getHeaders():
    supplied_key = api_key
    if not supplied_key:
        raise HTTPException(
            status_code=500,
            detail="Request is missing API key. You're not logged in!",
        )

    return {"Content-Type": "application/json", "api-key": api_key}

@urlAnalyzer.get("/health", response_class=JSONResponse)
def health():
    return JSONResponse(
        status_code=200,
        content={"message": "server is up!"}
    )

# TODO: handle status code 429, quota reached
@urlAnalyzer.post(
    "/analyze",
    response_model=AnalyzeResponse,
)
async def analyze(request: AnalyzeRequest):

    headers=getHeaders() # check for API key

    # Submit scan
    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.post(
            f"{URLSCAN_BASE_URL}/scan/",
            headers=headers,
            json={
                "url": request.url,
                "visibility": "unlisted",
            },
        )
    # Scan is successful
    if response.status_code == 200:
        data = response.json()

        # Return error message for missing scan ID
        if not data.get("uuid"):
            return {
                "url": str(request.url),
                "verdict": "unknown",
                "message": "No scan results found."
            }
        
        uuid = response.json()["uuid"]
        
        # Poll until result is available
        async with httpx.AsyncClient(timeout=30) as client:
            for _ in range(30):
                result_response = await client.get(
                    url=f"{URLSCAN_BASE_URL}/result/{uuid}/",
                    headers={"api-key": api_key}
                )

                # Scan is ready
                if result_response.status_code == 200:
                    break
                # Request for scan results failed
                if result_response.status_code != 404:
                     # Return status code and error message
                    return JSONResponse(
                        status_code=response.status_code,
                        content=error_data
                    )

                await sleep(2)
            else:
                return JSONResponse (
                    status_code=408,
                    content={"message":"Scan timed out."}
                )

        result = result_response.json()

        # Overall verdict
        overall = result.get("verdicts", {}).get("overall", {})

        score = overall.get("score", 0)

        malicious = overall.get("malicious")

        return AnalyzeResponse(
            url=request.url,
            uuid=uuid,
            malicious=malicious,
            score=score,
        )
    elif response.status_code == 429:
        
        return JSONResponse(
            status_code=response.status_code,
            content={"message": "You have reached your maximum quota of scans."}
        )
    # Scan request failed
    else:
        # Return status code and error message 
        error_data = response.json()
       
        return JSONResponse(
            status_code=response.status_code,
            content=error_data
        )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run( "api:urlAnalyzer", host="127.0.0.1", port=8000, reload=True)
