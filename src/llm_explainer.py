#Ashely's Code
def generate_explanation(text, risk_score):
    # placeholder for TinyLlama / Gemma
    if risk_score > 70:
        return "This message is likely a smishing attack. It uses urgency or suspicious patterns."
    elif risk_score > 40:
        return "This message has some suspicious characteristics."
    else:
        return "This message appears safe."