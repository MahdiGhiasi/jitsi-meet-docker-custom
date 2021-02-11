FROM python:3.8
ADD ./requirements.txt /requirements.txt
WORKDIR /
RUN pip install -r requirements.txt
RUN mkdir /user-logger
ADD main.py /user-logger/
RUN mkdir /user-logger/logs
WORKDIR /user-logger
CMD python main.py
